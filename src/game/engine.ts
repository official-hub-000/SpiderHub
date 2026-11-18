import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { AudioEngine } from './audio';
import { createCityWorld, type CityWorld } from './city';
import { NpcSystem } from './npcs';
import { PlayerController } from './player';
import {
  buildDiagnosticReport,
  clearSaveFromStorage,
  computeThreatLevel,
  loadSaveFromStorage,
  writeSaveToStorage,
} from './save';
import { ShotRail, StorySystem } from './story';
import type {
  Dimension,
  GameMode,
  HudSnapshot,
  InputState,
  ProjectedLabel,
  SaveGame,
  SpireMarker,
} from './types';

const _projVec = new THREE.Vector3();
const _spireVec = new THREE.Vector3();
const _camFwd = new THREE.Vector3();
const _camRight = new THREE.Vector3();

function detectSoftwareWebGL(): { isSoftware: boolean; gpuName: string } {
  const probe = document.createElement('canvas');
  const gl = probe.getContext('webgl2');
  if (!gl) return { isSoftware: true, gpuName: 'No WebGL2' };
  const ext = gl.getExtension('WEBGL_debug_renderer_info');
  const param = ext ? ext.UNMASKED_RENDERER_WEBGL : gl.RENDERER;
  const gpu = String(gl.getParameter(param) || 'Unknown');
  gl.getExtension('WEBGL_lose_context')?.loseContext();
  const isSoftware = /SwiftShader|llvmpipe|softpipe|Software|Basic Render/i.test(
    gpu
  );
  return { isSoftware, gpuName: gpu };
}

export class WebverseEngine {
  private container: HTMLElement;
  private renderer: THREE.WebGLRenderer;
  private scene: THREE.Scene;
  private camera: THREE.PerspectiveCamera;
  private pmremGenerator: THREE.PMREMGenerator;
  private envTarget: THREE.WebGLRenderTarget;
  private isSoftwareGl: boolean;
  public readonly gpuName: string;

  private city: CityWorld;
  private player: PlayerController;
  private npcs: NpcSystem;
  private story = new StorySystem();
  private rail = new ShotRail();
  public readonly audio = new AudioEngine();

  private mode: GameMode = 'title';
  private dimension: Dimension = 0;
  private dimensionBanner = '';
  private dimensionBannerTimer = 0;

  private shardsCollected = 0;
  private kills = 0;
  private trust = 0;
  private citySaved = false;
  private timePlayed = 0;
  private autosaveTimer = 0;
  private movieTake = 1;
  private dialogue: { name: string; line: string } | null = null;
  private dialogueTimer = 0;

  private keys = new Set<string>();
  private virtualInput = {
    moveX: 0,
    moveY: 0,
    web: false,
    jump: false,
    attack: false,
    talk: false,
  };
  private pendingLookDX = 0;
  private pendingLookDY = 0;
  private draggingLook = false;
  private lastPointerX = 0;
  private lastPointerY = 0;
  private pointerLocked = false;

  private frameId = 0;
  private lastTime = performance.now();
  private elapsed = 0;
  private fpsSmooth = 60;
  private hudThrottle = 0;
  private onHudUpdate: (hud: HudSnapshot) => void;
  private disposed = false;

  constructor(
    container: HTMLElement,
    onHudUpdate: (hud: HudSnapshot) => void
  ) {
    this.container = container;
    this.onHudUpdate = onHudUpdate;

    const probe = detectSoftwareWebGL();
    this.isSoftwareGl = probe.isSoftware;
    this.gpuName = probe.gpuName;

    this.renderer = new THREE.WebGLRenderer({
      antialias: !this.isSoftwareGl,
      powerPreference: 'high-performance',
    });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.1;
    this.renderer.setPixelRatio(
      this.isSoftwareGl ? 1 : Math.min(window.devicePixelRatio, 2)
    );
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.shadowMap.enabled = !this.isSoftwareGl;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    container.appendChild(this.renderer.domElement);

    this.scene = new THREE.Scene();
    this.scene.fog = new THREE.Fog(0x141830, 32, 260);

    this.camera = new THREE.PerspectiveCamera(
      64,
      Math.max(1, container.clientWidth) / Math.max(1, container.clientHeight),
      0.2,
      700
    );

    // Mandatory RoomEnvironment reflections
    this.pmremGenerator = new THREE.PMREMGenerator(this.renderer);
    const roomEnv = new RoomEnvironment();
    this.envTarget = this.pmremGenerator.fromScene(roomEnv, 0.04);
    this.scene.environment = this.envTarget.texture;
    roomEnv.dispose();

    // Warm/cool studio city lighting rig
    const hemi = new THREE.HemisphereLight(0x88b4ff, 0x1e1b2e, 0.75);
    const keyLight = new THREE.DirectionalLight(0xffeedd, 1.35);
    keyLight.position.set(55, 95, 42);
    const rimLight = new THREE.DirectionalLight(0x38bdf8, 0.55);
    rimLight.position.set(-45, 35, -55);
    this.scene.add(hemi, keyLight, rimLight);

    // City, Hero, NPCs
    this.city = createCityWorld();
    this.scene.add(this.city.group);

    this.player = new PlayerController();
    this.player.reset(this.city.spawn);
    this.player.attachToScene(this.scene);

    this.npcs = new NpcSystem();
    this.scene.add(this.npcs.group);
    this.npcs.spawnCity(
      { x: 0, y: 0, z: 0 },
      this.city.extent * 0.7,
      7777,
      this.city.colliders
    );

    if (!this.isSoftwareGl) {
      this.renderer.compile(this.scene, this.camera);
    }

    this.bindEvents();
    this.emitHud();
    this.startLoop();
  }

  private bindEvents(): void {
    window.addEventListener('resize', this.handleResize);
    window.addEventListener('keydown', this.handleKeyDown);
    window.addEventListener('keyup', this.handleKeyUp);
    document.addEventListener('pointerlockchange', this.handlePointerLockChange);

    const dom = this.renderer.domElement;
    dom.addEventListener('pointerdown', this.handlePointerDown);
    window.addEventListener('pointermove', this.handlePointerMove);
    window.addEventListener('pointerup', this.handlePointerUp);
    dom.addEventListener('contextmenu', this.handleContextMenu);
  }

  private handleContextMenu = (e: MouseEvent): void => {
    e.preventDefault();
  };

  private handleResize = (): void => {
    if (this.disposed) return;
    const w = Math.max(1, this.container.clientWidth);
    const h = Math.max(1, this.container.clientHeight);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(w, h);
  };

  private handlePointerLockChange = (): void => {
    this.pointerLocked =
      document.pointerLockElement === this.renderer.domElement;
    this.emitHud();
  };

  private handleKeyDown = (e: KeyboardEvent): void => {
    this.audio.unlock();
    const code = e.code;
    this.keys.add(code);

    if (code === 'Escape' || code === 'KeyP') {
      if (this.mode === 'playing') {
        this.pauseGame();
      } else if (this.mode === 'paused') {
        this.resumeGame();
      } else if (this.mode === 'cutscene' || this.mode === 'movie' || this.mode === 'ending') {
        this.skipSequence();
      }
      return;
    }

    if (this.mode === 'playing') {
      if (code === 'KeyF') {
        this.virtualInput.attack = true;
      } else if (code === 'KeyE') {
        this.virtualInput.talk = true;
      } else if (code === 'Space') {
        e.preventDefault();
      }
    }
  };

  private handleKeyUp = (e: KeyboardEvent): void => {
    this.keys.delete(e.code);
  };

  private handlePointerDown = (e: PointerEvent): void => {
    this.audio.unlock();
    if (this.mode !== 'playing') return;
    this.draggingLook = true;
    this.lastPointerX = e.clientX;
    this.lastPointerY = e.clientY;
    if (e.button === 2) {
      this.virtualInput.attack = true;
    }
  };

  private handlePointerMove = (e: PointerEvent): void => {
    if (this.mode !== 'playing') return;
    if (this.pointerLocked) {
      this.pendingLookDX += e.movementX;
      this.pendingLookDY += e.movementY;
    } else if (this.draggingLook) {
      this.pendingLookDX += e.clientX - this.lastPointerX;
      this.pendingLookDY += e.clientY - this.lastPointerY;
      this.lastPointerX = e.clientX;
      this.lastPointerY = e.clientY;
    }
  };

  private handlePointerUp = (): void => {
    this.draggingLook = false;
  };

  // --- Public Actions ---
  public requestPointerLock(): void {
    try {
      this.renderer.domElement.requestPointerLock?.();
    } catch {
      // Ignore if browser blocks pointer lock inside iframe
    }
  }

  public setVirtualMove(x: number, y: number): void {
    this.virtualInput.moveX = x;
    this.virtualInput.moveY = y;
  }

  public setVirtualAction(
    action: 'web' | 'jump' | 'attack' | 'talk',
    active: boolean
  ): void {
    this.audio.unlock();
    this.virtualInput[action] = active;
  }

  public toggleMute(): void {
    this.audio.unlock();
    this.audio.setMuted(!this.audio.muted);
    this.emitHud();
  }

  public startNewGame(): void {
    this.audio.unlock();
    clearSaveFromStorage();
    this.resetWorldState();
    this.mode = 'cutscene';
    this.rail.startOpeningCutscene();
    this.story.reset();
    this.emitHud();
  }

  public continueGame(): void {
    this.audio.unlock();
    const save = loadSaveFromStorage();
    if (!save) {
      this.startNewGame();
      return;
    }
    this.resetWorldState();
    this.shardsCollected = Math.min(this.city.shards.length, save.shards);
    for (let i = 0; i < this.shardsCollected; i++) {
      this.city.shards[i].collected = true;
      this.city.shards[i].mesh.visible = false;
    }
    this.trust = save.trust;
    this.kills = save.kills;
    this.citySaved = save.citySaved === 1;
    this.setDimension(save.dimension, false);
    this.timePlayed = save.timePlayed;
    this.player.reset({
      x: save.posX,
      y: Math.max(1.7, save.posY),
      z: save.posZ,
    });
    this.player.health = Math.max(25, Math.min(100, save.health));
    this.npcs.setThreat(save.level);
    this.mode = 'playing';
    this.emitHud();
  }

  public startMovie(take?: number): void {
    this.audio.unlock();
    const nextTake = take ?? (this.movieTake === 1 ? 2 : 1);
    this.movieTake = nextTake;
    this.setDimension(nextTake === 2 ? 1 : 0, false);
    this.rail.startMovie(nextTake);
    this.mode = 'movie';
    this.emitHud();
  }

  public skipSequence(): void {
    this.rail.skip();
    if (this.mode === 'cutscene') {
      this.mode = 'playing';
      this.saveProgress();
    } else if (this.mode === 'movie' || this.mode === 'ending') {
      this.mode = 'title';
      this.setDimension(0, false);
    }
    this.emitHud();
  }

  public pauseGame(): void {
    if (this.mode !== 'playing') return;
    this.mode = 'paused';
    if (document.pointerLockElement) {
      document.exitPointerLock?.();
    }
    this.emitHud();
  }

  public resumeGame(): void {
    if (this.mode !== 'paused') return;
    this.mode = 'playing';
    this.emitHud();
  }

  public saveAndQuit(): void {
    this.saveProgress();
    this.mode = 'title';
    this.setDimension(0, false);
    this.emitHud();
  }

  public restartRun(): void {
    this.resetWorldState();
    this.mode = 'playing';
    this.saveProgress();
    this.emitHud();
  }

  public quitToTitle(): void {
    this.mode = 'title';
    this.setDimension(0, false);
    if (document.pointerLockElement) {
      document.exitPointerLock?.();
    }
    this.emitHud();
  }

  public toggleDimensionManual(): void {
    this.audio.unlock();
    this.setDimension(this.dimension === 0 ? 1 : 0, true);
    this.audio.playPortalWarp();
    this.emitHud();
  }

  public getDiagnosticReport(): string {
    return buildDiagnosticReport(
      this.buildHudSnapshot(),
      this.player.position,
      this.gpuName
    );
  }

  private resetWorldState(): void {
    this.shardsCollected = 0;
    this.kills = 0;
    this.trust = 0;
    this.citySaved = false;
    this.timePlayed = 0;
    this.dialogue = null;
    this.dialogueTimer = 0;
    this.setDimension(0, false);
    for (const s of this.city.shards) {
      s.collected = false;
      s.mesh.visible = true;
    }
    this.player.reset(this.city.spawn);
    this.npcs.setThreat(1);
    this.npcs.spawnCity(
      { x: 0, y: 0, z: 0 },
      this.city.extent * 0.7,
      7777,
      this.city.colliders
    );
    this.story.reset();
  }

  private setDimension(dim: Dimension, showBanner: boolean): void {
    this.dimension = dim;
    const isInk = dim === 1;
    this.city.cityMaterial.uniforms.uComic.value = isInk ? 1.0 : 0.0;
    this.city.skyMaterial.uniforms.uInk.value = isInk ? 1.0 : 0.0;
    const fogHex = isInk ? 0x26102d : 0x141830;
    (this.scene.fog as THREE.Fog).color.setHex(fogHex);
    this.city.cityMaterial.uniforms.uFogColor.value.set(
      isInk ? 0.15 : 0.08,
      isInk ? 0.06 : 0.09,
      isInk ? 0.18 : 0.19
    );
    if (showBanner) {
      this.dimensionBanner = isInk
        ? 'DIMENSION SHIFT: INKVERSE'
        : 'DIMENSION SHIFT: NEON NOIR';
      this.dimensionBannerTimer = 3.0;
    }
  }

  private saveProgress(): void {
    const save: SaveGame = {
      version: 1,
      valid: 1,
      shards: this.shardsCollected,
      trust: this.trust,
      kills: this.kills,
      citySaved: this.citySaved ? 1 : 0,
      dimension: this.dimension,
      level: this.npcs.threat,
      posX: this.player.position.x,
      posY: this.player.position.y,
      posZ: this.player.position.z,
      health: this.player.health,
      timePlayed: this.timePlayed,
    };
    writeSaveToStorage(save);
  }

  private buildInputState(): InputState {
    let mx = this.virtualInput.moveX;
    let my = this.virtualInput.moveY;
    if (this.keys.has('KeyW') || this.keys.has('ArrowUp')) my += 1;
    if (this.keys.has('KeyS') || this.keys.has('ArrowDown')) my -= 1;
    if (this.keys.has('KeyD') || this.keys.has('ArrowRight')) mx += 1;
    if (this.keys.has('KeyA') || this.keys.has('ArrowLeft')) mx -= 1;

    const input: InputState = {
      moveX: Math.max(-1, Math.min(1, mx)),
      moveY: Math.max(-1, Math.min(1, my)),
      lookDX: this.pendingLookDX,
      lookDY: this.pendingLookDY,
      jump: this.keys.has('Space') || this.virtualInput.jump,
      web:
        this.keys.has('ShiftLeft') ||
        this.keys.has('ShiftRight') ||
        this.keys.has('KeyQ') ||
        this.virtualInput.web,
      attack: this.virtualInput.attack,
      talk: this.virtualInput.talk,
    };
    this.pendingLookDX = 0;
    this.pendingLookDY = 0;
    this.virtualInput.attack = false;
    this.virtualInput.talk = false;
    return input;
  }

  private stepSimulation(dt: number): void {
    this.elapsed += dt;
    this.city.cityMaterial.uniforms.uTime.value = this.elapsed;
    this.city.skyMaterial.uniforms.uTime.value = this.elapsed;

    // Animate portals, shards, and Spire beacon
    for (let i = 0; i < this.city.portals.length; i++) {
      const p = this.city.portals[i];
      p.cooldown = Math.max(0, p.cooldown - dt);
      p.group.rotation.y = this.elapsed * 0.8 + i;
      if (p.group.children[1]) {
        p.group.children[1].rotation.z = -this.elapsed * 1.6;
      }
    }
    for (let i = 0; i < this.city.shards.length; i++) {
      const s = this.city.shards[i];
      if (s.collected) continue;
      s.mesh.rotation.y = this.elapsed * 2.2 + i;
      s.mesh.position.y = s.pos.y + Math.sin(this.elapsed * 2.8 + i) * 0.32;
    }
    const pillarMat = this.city.spirePillar.material as THREE.MeshBasicMaterial;
    pillarMat.opacity = 0.16 + 0.1 * Math.sin(this.elapsed * 3.0);

    if (this.dimensionBannerTimer > 0) {
      this.dimensionBannerTimer = Math.max(0, this.dimensionBannerTimer - dt);
      if (this.dimensionBannerTimer === 0) this.dimensionBanner = '';
    }
    if (this.dialogueTimer > 0) {
      this.dialogueTimer = Math.max(0, this.dialogueTimer - dt);
      if (this.dialogueTimer === 0) this.dialogue = null;
    }

    // Mode-specific simulation
    if (this.mode === 'title') {
      // Title card: KAGE swings across the skyline in front of the Spire
      const angle = this.elapsed * 0.42;
      const r = 46;
      this.player.position = {
        x: Math.sin(angle) * r,
        y: 26 + Math.sin(angle * 2.0) * 8.0,
        z: Math.cos(angle) * r,
      };
      this.player.yaw = -angle + Math.PI * 0.5;
      this.player.grappling = Math.sin(angle * 2.0) > -0.2;
      this.player.anchor = {
        x: Math.sin(angle + 0.25) * (r * 0.72),
        y: 52,
        z: Math.cos(angle + 0.25) * (r * 0.72),
      };
      this.player.group.position.set(
        this.player.position.x,
        this.player.position.y,
        this.player.position.z
      );
      this.player.group.rotation.y = -this.player.yaw;
      if (this.player.grappling) {
        this.player.webLine.visible = true;
        const attr = this.player.webLine.geometry.getAttribute(
          'position'
        ) as THREE.BufferAttribute;
        attr.setXYZ(0, this.player.position.x, this.player.position.y + 0.2, this.player.position.z);
        attr.setXYZ(1, this.player.anchor.x, this.player.anchor.y, this.player.anchor.z);
        attr.needsUpdate = true;
      } else {
        this.player.webLine.visible = false;
      }
      this.camera.position.set(
        Math.sin(angle - 0.32) * 68,
        34 + Math.sin(this.elapsed * 0.25) * 4,
        Math.cos(angle - 0.32) * 68
      );
      this.camera.lookAt(0, 28, 0);
      this.audio.update(dt, false);
      return;
    }

    if (
      this.mode === 'cutscene' ||
      this.mode === 'movie' ||
      this.mode === 'ending'
    ) {
      const shot = this.rail.update(dt);
      if (!shot) {
        this.skipSequence();
        return;
      }
      this.camera.position.set(shot.pos.x, shot.pos.y, shot.pos.z);
      this.camera.lookAt(shot.look.x, shot.look.y, shot.look.z);
      this.audio.update(dt, true);
      return;
    }

    if (this.mode === 'paused' || this.mode === 'gameover') {
      this.audio.update(dt, false);
      return;
    }

    // --- Playing Mode ---
    this.timePlayed += dt;
    this.autosaveTimer += dt;
    if (this.autosaveTimer >= 8.0) {
      this.autosaveTimer = 0;
      this.saveProgress();
    }

    this.story.update(dt);
    const input = this.buildInputState();

    this.player.update(
      dt,
      input,
      this.dimension,
      this.city.colliders,
      () => this.audio.playWebThwip(),
      () => this.audio.playJump()
    );

    // Melee combat
    if (input.attack && this.player.attackCooldown === 0) {
      this.player.attackCooldown = 0.35;
      this.player.attackFlash = 0.22;
      this.audio.playMeleeSwing();
      const target = this.npcs.nearest(this.player.position, 4.4, true);
      if (target) {
        target.health -= 1.5;
        target.flash = 0.25;
        this.audio.playHitCrunch();
        if (target.health <= 0) {
          target.death = 0.01;
          this.kills++;
          this.trust += 1;
          this.updateThreat();
          this.saveProgress();
        }
      }
    }

    // Talk interaction
    if (input.talk) {
      const citizen = this.npcs.nearest(this.player.position, 5.0, false);
      if (citizen) {
        this.dialogue = { name: citizen.name, line: citizen.line };
        this.dialogueTimer = 5.0;
        if (!citizen.met) {
          citizen.met = true;
          this.trust += 1;
          this.saveProgress();
        }
        this.audio.playTone(440, 0.12, 0.14, 'triangle');
      } else if (this.nearSpireSeal()) {
        this.triggerSpireEnding();
        return;
      }
    }

    // Collect 24 data shards
    for (const s of this.city.shards) {
      if (s.collected) continue;
      const d = Math.hypot(
        s.pos.x - this.player.position.x,
        s.pos.y - this.player.position.y,
        s.pos.z - this.player.position.z
      );
      if (d < 3.0) {
        s.collected = true;
        s.mesh.visible = false;
        this.shardsCollected++;
        this.audio.playShardChime();
        this.updateThreat();
        this.saveProgress();
      }
    }

    // Dimension portal crossings
    for (const p of this.city.portals) {
      if (p.cooldown > 0) continue;
      const d = Math.hypot(
        p.pos.x - this.player.position.x,
        p.pos.y - this.player.position.y,
        p.pos.z - this.player.position.z
      );
      if (d < 3.2) {
        p.cooldown = 4.0;
        this.setDimension(this.dimension === 0 ? 1 : 0, true);
        this.audio.playPortalWarp();
        this.saveProgress();
        break;
      }
    }

    // Update NPCs & Husks
    this.npcs.update(
      dt,
      this.player.position,
      this.city.colliders,
      (dmg) => {
        this.player.health = Math.max(0, this.player.health - dmg);
        this.player.damageFlash = 1.0;
        this.audio.playDamage();
        if (this.player.health <= 0) {
          this.mode = 'gameover';
          this.saveProgress();
          this.emitHud();
        }
      },
      () => {
        this.trust = Math.max(0, this.trust - 1);
      }
    );

    // Update audio intensity based on speed + nearby enemies
    const speedNorm = Math.min(1, this.player.speed() / 30);
    const danger = this.npcs.nearest(this.player.position, 14, true) ? 0.5 : 0;
    this.audio.setIntensity(Math.max(0.2, Math.min(1, speedNorm * 0.7 + danger)));
    this.audio.update(dt, true);

    this.player.applyThirdPersonCamera(this.camera, this.city.colliders);
  }

  private nearSpireSeal(): boolean {
    const distXZ = Math.hypot(this.player.position.x, this.player.position.z);
    return distXZ < 10.5 && (this.player.position.y > 72 || distXZ < 8.0);
  }

  private triggerSpireEnding(): void {
    this.citySaved = true;
    this.updateThreat();
    this.saveProgress();
    this.audio.playSpireSeal();
    this.mode = 'ending';
    this.rail.startEndingCutscene();
    this.emitHud();
  }

  private updateThreat(): void {
    const tier = computeThreatLevel(
      this.shardsCollected,
      this.kills,
      this.citySaved
    );
    if (tier !== this.npcs.threat) {
      this.npcs.setThreat(tier);
    }
  }

  private buildHudSnapshot(): HudSnapshot {
    const w = this.container.clientWidth || 1;
    const h = this.container.clientHeight || 1;

    // Project floating NPC labels
    const labels: ProjectedLabel[] = [];
    if (this.mode === 'playing') {
      for (const n of this.npcs.npcs) {
        if (!n.active || n.death > 0) continue;
        const dist = Math.hypot(
          n.position.x - this.player.position.x,
          n.position.z - this.player.position.z
        );
        if (dist > 28) continue;
        _projVec.set(n.position.x, n.position.y + 2.25, n.position.z);
        _projVec.project(this.camera);
        if (_projVec.z > 1) continue;
        const sx = (_projVec.x * 0.5 + 0.5) * w;
        const sy = (-_projVec.y * 0.5 + 0.5) * h;
        if (sx < 20 || sx > w - 20 || sy < 20 || sy > h - 20) continue;
        labels.push({
          id: n.id,
          name: n.name,
          enemy: n.enemy,
          x: sx,
          y: sy,
          dist,
          canTalk: !n.enemy && dist < 5.0,
          healthRatio: n.maxHealth > 0 ? n.health / n.maxHealth : 1,
        });
      }
      labels.sort((a, b) => a.dist - b.dist);
      if (labels.length > 8) labels.length = 8;
    }

    // Spire objective marker
    _spireVec.set(0, 92, 0);
    const spireDist = Math.hypot(
      this.player.position.x,
      this.player.position.z
    );
    _spireVec.project(this.camera);
    const inFront = _spireVec.z <= 1;
    let sx = (_spireVec.x * 0.5 + 0.5) * w;
    let sy = (-_spireVec.y * 0.5 + 0.5) * h;
    if (!inFront) {
      sx = w - sx;
      sy = h - sy;
    }
    const onScreen =
      inFront && sx >= 36 && sx <= w - 36 && sy >= 36 && sy <= h - 36;
    const clampedX = Math.max(42, Math.min(w - 42, sx));
    const clampedY = Math.max(54, Math.min(h - 54, sy));

    this.camera.getWorldDirection(_camFwd);
    _camRight.crossVectors(_camFwd, this.camera.up).normalize();
    const bearingDeg = Math.round(
      (Math.atan2(-this.player.position.x, -this.player.position.z) * 180) /
        Math.PI
    );

    const nearCitizen = this.npcs.nearest(this.player.position, 5.0, false);
    let nearPortalDist = 999;
    for (const p of this.city.portals) {
      const d = Math.hypot(
        p.pos.x - this.player.position.x,
        p.pos.y - this.player.position.y,
        p.pos.z - this.player.position.z
      );
      if (d < nearPortalDist) nearPortalDist = d;
    }

    let subtitle = '';
    let actTitle = '';
    if (
      this.mode === 'cutscene' ||
      this.mode === 'movie' ||
      this.mode === 'ending'
    ) {
      subtitle = this.rail.subtitle();
      actTitle =
        this.mode === 'movie'
          ? `MOVIE MODE — TAKE ${this.movieTake} (${this.movieTake === 1 ? 'NEON NOIR' : 'INKVERSE'})`
          : this.mode === 'ending'
            ? 'THE SPIRE'
            : 'OPENING SEQUENCE';
    } else if (this.mode === 'playing') {
      subtitle = this.story.subtitle();
      actTitle = this.story.actTitle();
    }

    const spireMarker: SpireMarker = {
      x: clampedX,
      y: clampedY,
      onScreen,
      dist: Math.round(spireDist),
      bearingDeg,
    };

    return {
      mode: this.mode,
      dimension: this.dimension,
      dimensionBanner: this.dimensionBanner,
      health: this.player.health,
      maxHealth: this.player.maxHealth,
      shards: this.shardsCollected,
      totalShards: this.city.shards.length,
      kills: this.kills,
      aliveHusks: this.npcs.aliveEnemies(),
      aliveCitizens: this.npcs.aliveCitizens(),
      citizenDeaths: this.npcs.citizenDeaths,
      trust: this.trust,
      threatLevel: this.npcs.threat,
      citySaved: this.citySaved,
      timePlayed: this.timePlayed,
      speed: this.player.speed(),
      grappling: this.player.grappling,
      grounded: this.player.grounded,
      subtitle,
      actTitle,
      dialogue: this.dialogue,
      nearTalkName: nearCitizen ? nearCitizen.name : null,
      nearPortalDist,
      nearSpireSeal: this.nearSpireSeal(),
      movieTake: this.movieTake,
      labels,
      spireMarker,
      fps: this.fpsSmooth,
      hasSave: Boolean(loadSaveFromStorage()),
      muted: this.audio.muted,
      pointerLocked: this.pointerLocked,
    };
  }

  private emitHud(): void {
    this.onHudUpdate(this.buildHudSnapshot());
  }

  private afterDraw(cb: () => void): void {
    const gl = this.renderer.getContext() as WebGL2RenderingContext;
    if (!gl || !gl.fenceSync) {
      this.frameId = window.setTimeout(cb, 80);
      return;
    }
    const sync = gl.fenceSync(gl.SYNC_GPU_COMMANDS_COMPLETE, 0);
    gl.flush();
    const poll = () => {
      if (this.disposed) return;
      const state = sync && gl.getSyncParameter(sync, gl.SYNC_STATUS);
      if (state === gl.UNSIGNALED) {
        this.frameId = window.setTimeout(poll, 5);
      } else {
        if (sync) gl.deleteSync(sync);
        this.frameId = window.setTimeout(cb, 80);
      }
    };
    this.frameId = window.setTimeout(poll, 5);
  }

  private startLoop(): void {
    const tick = () => {
      if (this.disposed) return;
      const now = performance.now();
      const rawDt = (now - this.lastTime) / 1000;
      this.lastTime = now;
      const dt = Math.min(0.05, Math.max(0.001, rawDt));

      if (rawDt > 0.001) {
        const instFps = 1 / rawDt;
        this.fpsSmooth = this.fpsSmooth * 0.9 + Math.min(120, instFps) * 0.1;
      }

      this.stepSimulation(dt);
      this.renderer.render(this.scene, this.camera);

      this.hudThrottle += dt;
      if (this.hudThrottle >= 0.05) {
        this.hudThrottle = 0;
        this.emitHud();
      }

      if (this.isSoftwareGl) {
        this.afterDraw(tick);
      } else {
        this.frameId = requestAnimationFrame(tick);
      }
    };

    if (this.isSoftwareGl) {
      this.frameId = window.setTimeout(tick, 16);
    } else {
      this.frameId = requestAnimationFrame(tick);
    }
  }

  public dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.frameId);
    clearTimeout(this.frameId);

    window.removeEventListener('resize', this.handleResize);
    window.removeEventListener('keydown', this.handleKeyDown);
    window.removeEventListener('keyup', this.handleKeyUp);
    document.removeEventListener(
      'pointerlockchange',
      this.handlePointerLockChange
    );
    this.renderer.domElement.removeEventListener(
      'pointerdown',
      this.handlePointerDown
    );
    window.removeEventListener('pointermove', this.handlePointerMove);
    window.removeEventListener('pointerup', this.handlePointerUp);
    this.renderer.domElement.removeEventListener(
      'contextmenu',
      this.handleContextMenu
    );

    this.city.dispose();
    this.player.dispose();
    this.npcs.dispose();
    this.audio.dispose();
    this.envTarget.dispose();
    this.pmremGenerator.dispose();
    this.renderer.dispose();
    if (this.renderer.domElement.parentNode === this.container) {
      this.container.removeChild(this.renderer.domElement);
    }
  }
}
