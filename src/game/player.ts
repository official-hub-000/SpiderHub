import * as THREE from 'three';
import type { Aabb, Dimension, InputState, Vec3 } from './types';

// Reusable scratch vectors (zero GC in game loop)
const _toAnchor = new THREE.Vector3();
const _dir = new THREE.Vector3();
const _target = new THREE.Vector3();
const _boomDir = new THREE.Vector3();
const _sample = new THREE.Vector3();

export class PlayerController {
  public position: Vec3 = { x: 0, y: 1.7, z: 104 };
  public velocity: Vec3 = { x: 0, y: 0, z: 0 };
  public yaw = 0;
  public pitch = -0.05;
  public grounded = true;
  public grappling = false;
  public anchor: Vec3 = { x: 0, y: 12, z: 0 };
  public health = 100;
  public readonly maxHealth = 100;
  public attackFlash = 0;
  public attackCooldown = 0;
  public damageFlash = 0;

  private ropeLen = 10.0;
  private animPhase = 0;

  // Composite 3D Hero (KAGE) + Web line + Strike arc + Contact shadow
  public readonly group = new THREE.Group();
  public readonly webLine: THREE.Line;
  public readonly strikeRing: THREE.Mesh;
  private readonly shadowMesh: THREE.Mesh;
  private readonly leftArm: THREE.Group;
  private readonly rightArm: THREE.Group;
  private readonly leftLeg: THREE.Group;
  private readonly rightLeg: THREE.Group;
  private readonly leftWing: THREE.Mesh;
  private readonly rightWing: THREE.Mesh;

  private readonly disposables: { dispose: () => void }[] = [];

  constructor() {
    // Materials (brighter hero suit with cyan/crimson spider accents so KAGE reads against night city)
    const suitDark = new THREE.MeshStandardMaterial({
      color: 0x1e293b,
      roughness: 0.45,
      metalness: 0.35,
    });
    const suitRed = new THREE.MeshStandardMaterial({
      color: 0xef4444,
      emissive: 0x7f1d1d,
      emissiveIntensity: 0.45,
      roughness: 0.35,
      metalness: 0.3,
    });
    const visorMat = new THREE.MeshBasicMaterial({ color: 0x38bdf8 });
    const wingMat = new THREE.MeshBasicMaterial({
      color: 0x38bdf8,
      transparent: true,
      opacity: 0.45,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
    this.disposables.push(suitDark, suitRed, visorMat, wingMat);

    // Torso (primary silhouette)
    const torsoGeo = new THREE.BoxGeometry(0.56, 0.72, 0.3);
    const torso = new THREE.Mesh(torsoGeo, suitRed);
    torso.position.y = -0.35;
    this.group.add(torso);
    this.disposables.push(torsoGeo);

    // Chest spider emblem
    const emblemGeo = new THREE.OctahedronGeometry(0.14, 0);
    const emblem = new THREE.Mesh(emblemGeo, visorMat);
    emblem.position.set(0, -0.28, -0.16);
    emblem.scale.set(1.1, 1.3, 0.3);
    this.group.add(emblem);
    this.disposables.push(emblemGeo);

    // Head + glowing eyes
    const headGeo = new THREE.BoxGeometry(0.36, 0.38, 0.36);
    const head = new THREE.Mesh(headGeo, suitRed);
    head.position.y = 0.22;
    this.group.add(head);
    this.disposables.push(headGeo);

    const eyeGeo = new THREE.BoxGeometry(0.11, 0.07, 0.04);
    const eyeL = new THREE.Mesh(eyeGeo, visorMat);
    eyeL.position.set(-0.08, 0.24, -0.19);
    eyeL.rotation.z = -0.22;
    const eyeR = new THREE.Mesh(eyeGeo, visorMat);
    eyeR.position.set(0.08, 0.24, -0.19);
    eyeR.rotation.z = 0.22;
    this.group.add(eyeL, eyeR);
    this.disposables.push(eyeGeo);

    // Arms
    const armGeo = new THREE.BoxGeometry(0.16, 0.62, 0.16);
    this.disposables.push(armGeo);

    this.leftArm = new THREE.Group();
    this.leftArm.position.set(-0.37, -0.08, 0);
    const lArmMesh = new THREE.Mesh(armGeo, suitDark);
    lArmMesh.position.y = -0.26;
    this.leftArm.add(lArmMesh);
    this.group.add(this.leftArm);

    this.rightArm = new THREE.Group();
    this.rightArm.position.set(0.37, -0.08, 0);
    const rArmMesh = new THREE.Mesh(armGeo, suitDark);
    rArmMesh.position.y = -0.26;
    this.rightArm.add(rArmMesh);
    this.group.add(this.rightArm);

    // Legs
    const legGeo = new THREE.BoxGeometry(0.2, 0.72, 0.2);
    this.disposables.push(legGeo);

    this.leftLeg = new THREE.Group();
    this.leftLeg.position.set(-0.16, -0.72, 0);
    const lLegMesh = new THREE.Mesh(legGeo, suitDark);
    lLegMesh.position.y = -0.34;
    this.leftLeg.add(lLegMesh);
    this.group.add(this.leftLeg);

    this.rightLeg = new THREE.Group();
    this.rightLeg.position.set(0.16, -0.72, 0);
    const rLegMesh = new THREE.Mesh(legGeo, suitDark);
    rLegMesh.position.y = -0.34;
    this.rightLeg.add(rLegMesh);
    this.group.add(this.rightLeg);

    // Web wings (flare during swing / glide)
    const wingGeo = new THREE.BufferGeometry();
    wingGeo.setAttribute(
      'position',
      new THREE.BufferAttribute(
        new Float32Array([0, 0, 0, 0.55, 0.15, 0.15, 0.15, -0.45, 0.08]),
        3
      )
    );
    wingGeo.computeVertexNormals();
    this.disposables.push(wingGeo);

    this.leftWing = new THREE.Mesh(wingGeo, wingMat);
    this.leftWing.position.set(-0.24, -0.18, 0.08);
    this.leftWing.scale.x = -1;
    this.rightWing = new THREE.Mesh(wingGeo, wingMat);
    this.rightWing.position.set(0.24, -0.18, 0.08);
    this.group.add(this.leftWing, this.rightWing);

    // Contact shadow
    const shadowGeo = new THREE.CircleGeometry(0.65, 20);
    const shadowMat = new THREE.MeshBasicMaterial({
      color: 0x000000,
      transparent: true,
      opacity: 0.45,
      depthWrite: false,
    });
    this.shadowMesh = new THREE.Mesh(shadowGeo, shadowMat);
    this.shadowMesh.rotation.x = -Math.PI / 2;
    this.disposables.push(shadowGeo, shadowMat);

    // Web line
    const webGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(),
      new THREE.Vector3(0, 10, 0),
    ]);
    const webMat = new THREE.LineBasicMaterial({ color: 0xe2e8f0 });
    this.webLine = new THREE.Line(webGeo, webMat);
    this.webLine.visible = false;
    this.webLine.frustumCulled = false;
    this.disposables.push(webGeo, webMat);

    // Melee strike ring
    const strikeGeo = new THREE.RingGeometry(0.8, 2.8, 24);
    const strikeMat = new THREE.MeshBasicMaterial({
      color: 0x38bdf8,
      transparent: true,
      opacity: 0.8,
      side: THREE.DoubleSide,
      depthWrite: false,
    });
    this.strikeRing = new THREE.Mesh(strikeGeo, strikeMat);
    this.strikeRing.rotation.x = -Math.PI / 2;
    this.strikeRing.visible = false;
    this.disposables.push(strikeGeo, strikeMat);
  }

  attachToScene(scene: THREE.Scene): void {
    scene.add(this.group, this.webLine, this.strikeRing, this.shadowMesh);
  }

  forward(): Vec3 {
    const cp = Math.cos(this.pitch);
    return {
      x: Math.sin(this.yaw) * cp,
      y: Math.sin(this.pitch),
      z: -Math.cos(this.yaw) * cp,
    };
  }

  speed(): number {
    return Math.hypot(this.velocity.x, this.velocity.y, this.velocity.z);
  }

  reset(pos: Vec3): void {
    this.position = { ...pos };
    this.velocity = { x: 0, y: 0, z: 0 };
    this.yaw = 0;
    this.pitch = -0.05;
    this.grounded = true;
    this.grappling = false;
    this.health = this.maxHealth;
    this.damageFlash = 0;
    this.attackFlash = 0;
  }

  update(
    dt: number,
    input: InputState,
    dimension: Dimension,
    colliders: Aabb[],
    onWebShot?: () => void,
    onJump?: () => void
  ): void {
    if (dt <= 0) return;

    this.attackFlash = Math.max(0, this.attackFlash - dt);
    this.attackCooldown = Math.max(0, this.attackCooldown - dt);
    this.damageFlash = Math.max(0, this.damageFlash - dt * 1.8);

    // Look
    this.yaw += input.lookDX * 0.0045;
    this.pitch = Math.max(
      -1.45,
      Math.min(1.45, this.pitch - input.lookDY * 0.0045)
    );

    const f = this.forward();
    let flatFx = f.x;
    let flatFz = f.z;
    const flatLen = Math.hypot(flatFx, flatFz);
    if (flatLen < 0.01) {
      flatFx = 0;
      flatFz = -1;
    } else {
      flatFx /= flatLen;
      flatFz /= flatLen;
    }
    // right = normalize(cross(flatF, (0,1,0))) = (-flatFz, 0, flatFx)
    const rightX = -flatFz;
    const rightZ = flatFx;

    let wishX = flatFx * input.moveY + rightX * input.moveX;
    let wishZ = flatFz * input.moveY + rightZ * input.moveX;
    const wishLen = Math.hypot(wishX, wishZ);
    if (wishLen > 1.0) {
      wishX /= wishLen;
      wishZ /= wishLen;
    }

    const moveSpeed = 9.5;
    const control = this.grappling ? 0.12 : this.grounded ? 1.0 : 0.38;
    const blend = Math.min(1.0, dt * 10.0) * control;
    this.velocity.x += (wishX * moveSpeed - this.velocity.x) * blend;
    this.velocity.z += (wishZ * moveSpeed - this.velocity.z) * blend;

    // Dimension gravity: Neon Noir has low-gravity long swings (24), Inkverse has heavier comic plunges (32)
    const gravity = dimension === 1 ? 31.0 : 24.0;
    this.velocity.y -= gravity * dt;

    // Jump
    if (input.jump && this.grounded) {
      this.velocity.y = 10.0;
      this.grounded = false;
      onJump?.();
    }

    // Web grapple
    if (input.web) {
      if (!this.grappling) {
        this.grappling = true;
        this.anchor = {
          x: this.position.x + f.x * 16.0,
          y: this.position.y + f.y * 16.0 + 7.5,
          z: this.position.z + f.z * 16.0,
        };
        const dx0 = this.anchor.x - this.position.x;
        const dy0 = this.anchor.y - this.position.y;
        const dz0 = this.anchor.z - this.position.z;
        const d0 = Math.hypot(dx0, dy0, dz0);
        this.ropeLen = Math.max(4.0, d0 * 0.92);
        if (d0 > 0.001) {
          this.velocity.x += (dx0 / d0) * 7.5;
          this.velocity.y += (dy0 / d0) * 7.5;
          this.velocity.z += (dz0 / d0) * 7.5;
        }
        onWebShot?.();
      }

      const ax = this.anchor.x - this.position.x;
      const ay = this.anchor.y - this.position.y;
      const az = this.anchor.z - this.position.z;
      const d = Math.hypot(ax, ay, az);
      if (d > 0.001) {
        const dx = ax / d;
        const dy = ay / d;
        const dz = az / d;
        const stretch = d / Math.max(0.001, this.ropeLen);
        const reel = 22.0 + 30.0 * Math.max(0.0, stretch - 1.0);
        this.velocity.x += dx * reel * dt;
        this.velocity.y += dy * reel * dt;
        this.velocity.z += dz * reel * dt;

        // Swing pump
        this.velocity.x += flatFx * (input.moveY * 16.0 * dt) + rightX * (input.moveX * 20.0 * dt);
        this.velocity.z += flatFz * (input.moveY * 16.0 * dt) + rightZ * (input.moveX * 20.0 * dt);

        if (d > this.ropeLen) {
          this.position.x = this.anchor.x - dx * this.ropeLen;
          this.position.y = this.anchor.y - dy * this.ropeLen;
          this.position.z = this.anchor.z - dz * this.ropeLen;
          const radial =
            this.velocity.x * dx + this.velocity.y * dy + this.velocity.z * dz;
          if (radial < 0) {
            this.velocity.x -= dx * radial;
            this.velocity.y -= dy * radial;
            this.velocity.z -= dz * radial;
          }
        }
      }

      const sp = this.speed();
      const kMaxSwing = 34.0;
      if (sp > kMaxSwing) {
        const k = kMaxSwing / sp;
        this.velocity.x *= k;
        this.velocity.y *= k;
        this.velocity.z *= k;
      }
    } else if (this.grappling) {
      this.grappling = false;
      this.velocity.y += 2.6;
    }

    this.position.x += this.velocity.x * dt;
    this.position.y += this.velocity.y * dt;
    this.position.z += this.velocity.z * dt;

    // City perimeter soft clamp
    const bound = 128;
    this.position.x = Math.max(-bound, Math.min(bound, this.position.x));
    this.position.z = Math.max(-bound, Math.min(bound, this.position.z));

    const groundY = 1.7;
    if (this.position.y <= groundY) {
      this.position.y = groundY;
      if (this.velocity.y < 0) this.velocity.y = 0;
      this.grounded = true;
    } else {
      this.grounded = false;
    }

    this.collide(colliders);
    this.syncVisuals(dt, colliders);
  }

  private collide(boxes: Aabb[]): void {
    const r = 0.42;
    const bodyBottom = this.position.y - 1.7;
    const bodyTop = this.position.y + 0.2;

    let floor = 1.7;
    for (const b of boxes) {
      if (
        this.position.x < b.min.x - r ||
        this.position.x > b.max.x + r ||
        this.position.z < b.min.z - r ||
        this.position.z > b.max.z + r
      ) {
        continue;
      }
      if (bodyTop < b.min.y || bodyBottom > b.max.y - 0.35) continue;
      if (b.max.y > floor && this.position.y >= b.max.y - 0.6) {
        floor = b.max.y;
      }
    }
    if (this.position.y <= floor + 0.02 && this.velocity.y <= 0) {
      this.position.y = floor;
      this.velocity.y = 0;
      this.grounded = true;
    }

    for (const b of boxes) {
      if (bodyTop < b.min.y || bodyBottom > b.max.y - 0.35) continue;
      const cx = Math.max(b.min.x, Math.min(b.max.x, this.position.x));
      const cz = Math.max(b.min.z, Math.min(b.max.z, this.position.z));
      const dx = this.position.x - cx;
      const dz = this.position.z - cz;
      const d2 = dx * dx + dz * dz;
      if (d2 > r * r) continue;
      if (d2 > 1e-6) {
        const d = Math.sqrt(d2);
        this.position.x = cx + (dx / d) * r;
        this.position.z = cz + (dz / d) * r;
      } else {
        const pxl = this.position.x - b.min.x + r;
        const pxr = b.max.x - this.position.x + r;
        const pzl = this.position.z - b.min.z + r;
        const pzr = b.max.z - this.position.z + r;
        const m = Math.min(pxl, pxr, pzl, pzr);
        if (m === pxl) this.position.x = b.min.x - r;
        else if (m === pxr) this.position.x = b.max.x + r;
        else if (m === pzl) this.position.z = b.min.z - r;
        else this.position.z = b.max.z + r;
      }
      this.velocity.x *= 0.2;
      this.velocity.z *= 0.2;
    }
  }

  private syncVisuals(dt: number, boxes: Aabb[]): void {
    this.group.position.set(this.position.x, this.position.y, this.position.z);
    // Face movement or camera yaw
    this.group.rotation.y = -this.yaw;

    const horizSpeed = Math.hypot(this.velocity.x, this.velocity.z);
    this.animPhase += dt * (2.5 + horizSpeed * 0.9);

    if (this.grappling) {
      // Swinging pose: right arm raised toward web, knees tucked
      this.rightArm.rotation.x = -2.4;
      this.leftArm.rotation.x = 0.6;
      this.leftLeg.rotation.x = 0.65;
      this.rightLeg.rotation.x = 0.35;
      this.leftWing.visible = true;
      this.rightWing.visible = true;
      this.group.rotation.x = -0.25;
    } else if (!this.grounded) {
      this.leftArm.rotation.x = -1.2;
      this.rightArm.rotation.x = -1.2;
      this.leftLeg.rotation.x = 0.4;
      this.rightLeg.rotation.x = -0.3;
      this.leftWing.visible = true;
      this.rightWing.visible = true;
      this.group.rotation.x = -0.12;
    } else {
      const stride = Math.sin(this.animPhase) * Math.min(1, horizSpeed / 7.0) * 0.75;
      this.leftLeg.rotation.x = stride;
      this.rightLeg.rotation.x = -stride;
      this.leftArm.rotation.x = -stride * 0.8;
      this.rightArm.rotation.x =
        this.attackFlash > 0 ? -1.8 : stride * 0.8;
      this.leftWing.visible = false;
      this.rightWing.visible = false;
      this.group.rotation.x = 0;
    }

    // Contact shadow on nearest floor/roof beneath player
    let shadowY = 0.04;
    for (const b of boxes) {
      if (
        this.position.x >= b.min.x &&
        this.position.x <= b.max.x &&
        this.position.z >= b.min.z &&
        this.position.z <= b.max.z &&
        b.max.y <= this.position.y - 1.5 &&
        b.max.y > shadowY
      ) {
        shadowY = b.max.y + 0.03;
      }
    }
    const heightAbove = Math.max(0, this.position.y - 1.7 - shadowY);
    this.shadowMesh.position.set(this.position.x, shadowY, this.position.z);
    const sScale = Math.max(0.35, 1.0 - heightAbove * 0.04);
    this.shadowMesh.scale.setScalar(sScale);

    // Web line
    if (this.grappling) {
      this.webLine.visible = true;
      const posAttr = this.webLine.geometry.getAttribute(
        'position'
      ) as THREE.BufferAttribute;
      posAttr.setXYZ(0, this.position.x, this.position.y + 0.25, this.position.z);
      posAttr.setXYZ(1, this.anchor.x, this.anchor.y, this.anchor.z);
      posAttr.needsUpdate = true;
    } else {
      this.webLine.visible = false;
    }

    // Melee strike ring
    if (this.attackFlash > 0) {
      this.strikeRing.visible = true;
      this.strikeRing.position.set(
        this.position.x,
        this.position.y - 0.7,
        this.position.z
      );
      const t = 1 - this.attackFlash / 0.22;
      this.strikeRing.scale.setScalar(0.7 + t * 1.1);
    } else {
      this.strikeRing.visible = false;
    }
  }

  applyThirdPersonCamera(camera: THREE.PerspectiveCamera, boxes: Aabb[]): void {
    const f = this.forward();
    _target.set(this.position.x, this.position.y + 0.55, this.position.z);
    const dist = 6.5;
    _boomDir.set(-f.x, -f.y + 0.24, -f.z).normalize();

    const radius = 0.45;
    let t = dist;
    for (let i = 1; i <= 16; i++) {
      const s = (dist * i) / 16.0;
      _sample.copy(_target).addScaledVector(_boomDir, s);
      let blocked = false;
      for (const b of boxes) {
        if (
          _sample.x > b.min.x - radius &&
          _sample.x < b.max.x + radius &&
          _sample.y > b.min.y - radius &&
          _sample.y < b.max.y + radius &&
          _sample.z > b.min.z - radius &&
          _sample.z < b.max.z + radius
        ) {
          blocked = true;
          break;
        }
      }
      if (blocked) {
        t = Math.max(1.6, (dist * (i - 1)) / 16.0 - 0.2);
        break;
      }
    }

    camera.position.copy(_target).addScaledVector(_boomDir, t);
    if (camera.position.y < 1.0) camera.position.y = 1.0;

    // Look slightly ahead of KAGE
    _toAnchor.copy(_target).addScaledVector(
      _dir.set(f.x, f.y, f.z),
      2.5
    );
    camera.lookAt(_toAnchor);

    // Velocity-driven FOV kick
    const sp = this.speed();
    const targetFov = 64 + Math.min(20, Math.max(0, sp - 8) * 0.85);
    if (Math.abs(camera.fov - targetFov) > 0.05) {
      camera.fov += (targetFov - camera.fov) * 0.14;
      camera.updateProjectionMatrix();
    }
  }

  dispose(): void {
    this.disposables.forEach((d) => d.dispose());
  }
}
