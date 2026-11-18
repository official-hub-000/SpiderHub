import * as THREE from 'three';
import { Pcg32 } from './rng';
import type { Aabb, Vec3 } from './types';

export interface NpcEntity {
  id: string;
  position: Vec3;
  target: Vec3;
  yaw: number;
  bob: number;
  speed: number;
  health: number;
  maxHealth: number;
  flash: number;
  death: number;
  attackCd: number;
  active: boolean;
  enemy: boolean;
  met: boolean;
  panic: number;
  talk: number;
  name: string;
  line: string;
  mesh: THREE.Group;
  bodyMat: THREE.MeshStandardMaterial;
}

const CITIZEN_NAMES = [
  'MIRA',
  'JUN',
  'SORA',
  'REX',
  'AYA',
  'KENTA',
  'NOA',
  'YUI',
  'PIKO',
  'DARA',
  'OTO',
  'LENA',
  'BASIM',
  'HANA',
  'ROOK',
  'IVY',
];

const CITIZEN_LINES = [
  'THE PORTALS ARE GETTING WORSE.',
  'I SAW A HERO SWINGING ON A WEB LAST NIGHT.',
  'STAY OUT OF THE UNDERFLOW AFTER DARK.',
  'HALCYON SAYS THE CITY IS FINE. IT IS NOT.',
  'THE LANTERNKEEPERS ARE WATCHING.',
  'IF YOU SEE A GLITCH, RUN.',
  'THE MULTIVERSE IS BLEEDING INTO OURS.',
  'YOU LOOK LIKE YOU CAN HANDLE A FIGHT.',
  'THE SECTOR SOLD OUT IN 4 MINUTES. WE GOT NOTHING.',
  'WHY DOES THE SKY CHANGE WHEN THOSE RINGS OPEN?',
  'I HID MY FAMILY IN THE OLD SUBWAY. TELL NO ONE.',
  'IF THE HERO OF THE WEB IS REAL, WHY ARE YOU HERE?',
  'STREETLIGHT 7 IS DEAD AGAIN. DO NOT STAND UNDER IT.',
  'A PORTAL TOOK MY NEIGHBOUR. THE NEIGHBOUR IS FINE NOW.',
  'HALCYON PUT A BOUNTY ON ANYONE WHO SWINGS.',
  'RUN IF YOU HEAR A SOUND LIKE RAIN IN A ROOM.',
];

const ENEMY_NAMES = ['HUSK', 'RAZOR', 'NULL', 'VEX', 'GREY', 'TOLL'];

export class NpcSystem {
  public npcs: NpcEntity[] = [];
  public citizenDeaths = 0;
  public threat = 1;
  public readonly group = new THREE.Group();

  private rng = new Pcg32(9911);
  private reinforceTimer = 0;
  private nextId = 1;

  private readonly sharedGeos: THREE.BufferGeometry[] = [];
  private readonly sharedMats: THREE.Material[] = [];
  private readonly shadowGeo: THREE.CircleGeometry;
  private readonly shadowMat: THREE.MeshBasicMaterial;
  private readonly citizenVisorMat: THREE.MeshBasicMaterial;
  private readonly enemyVisorMat: THREE.MeshBasicMaterial;

  constructor() {
    const torsoGeo = new THREE.BoxGeometry(0.46, 0.78, 0.28);
    const headGeo = new THREE.BoxGeometry(0.32, 0.34, 0.32);
    const legGeo = new THREE.BoxGeometry(0.36, 0.68, 0.24);
    const visorGeo = new THREE.BoxGeometry(0.24, 0.08, 0.06);
    this.shadowGeo = new THREE.CircleGeometry(0.48, 16);
    this.shadowMat = new THREE.MeshBasicMaterial({
      color: 0x000000,
      transparent: true,
      opacity: 0.42,
      depthWrite: false,
    });
    this.citizenVisorMat = new THREE.MeshBasicMaterial({ color: 0x38bdf8 });
    this.enemyVisorMat = new THREE.MeshBasicMaterial({ color: 0xf43f5e });

    this.sharedGeos.push(torsoGeo, headGeo, legGeo, visorGeo, this.shadowGeo);
    this.sharedMats.push(
      this.shadowMat,
      this.citizenVisorMat,
      this.enemyVisorMat
    );
  }

  private createNpcMesh(enemy: boolean): {
    mesh: THREE.Group;
    bodyMat: THREE.MeshStandardMaterial;
  } {
    const g = new THREE.Group();
    const bodyMat = new THREE.MeshStandardMaterial({
      color: enemy ? 0x1f1224 : 0x334155,
      emissive: enemy ? 0x7f1d1d : 0x0f172a,
      emissiveIntensity: enemy ? 0.55 : 0.2,
      roughness: 0.55,
      metalness: 0.25,
    });
    const accentMat = new THREE.MeshStandardMaterial({
      color: enemy ? 0xbe123c : 0x0ea5e9,
      roughness: 0.4,
      metalness: 0.4,
    });
    this.sharedMats.push(bodyMat, accentMat);

    const legs = new THREE.Mesh(this.sharedGeos[2], bodyMat);
    legs.position.y = 0.34;
    const torso = new THREE.Mesh(this.sharedGeos[0], accentMat);
    torso.position.y = 1.06;
    const head = new THREE.Mesh(this.sharedGeos[1], bodyMat);
    head.position.y = 1.62;
    const visor = new THREE.Mesh(
      this.sharedGeos[3],
      enemy ? this.enemyVisorMat : this.citizenVisorMat
    );
    visor.position.set(0, 1.64, -0.17);
    const shadow = new THREE.Mesh(this.shadowGeo, this.shadowMat);
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.y = 0.03;

    g.add(legs, torso, head, visor, shadow);
    return { mesh: g, bodyMat };
  }

  setThreat(level: number): void {
    this.threat = Math.max(1, Math.min(5, level));
    for (const n of this.npcs) {
      if (!n.enemy || !n.active) continue;
      n.speed = 2.6 + this.threat * 0.22;
      n.maxHealth = 3.0 + this.threat * 0.6;
      n.health = Math.max(n.health, n.maxHealth);
    }
  }

  spawnCity(center: Vec3, extent: number, seed: number, colliders: Aabb[]): void {
    this.clear();
    this.rng = new Pcg32(seed);
    this.citizenDeaths = 0;

    const friends = 26;
    const enemies = 6 + this.threat * 4;
    for (let i = 0; i < friends + enemies; i++) {
      this.spawnOne(i >= friends, center, extent, i, colliders);
    }
  }

  private spawnOne(
    enemy: boolean,
    center: Vec3,
    extent: number,
    idx: number,
    colliders: Aabb[]
  ): void {
    const spread =
      extent *
      (enemy ? 0.95 : 0.72) *
      Math.pow(this.rng.nextFloat(), 0.65);
    const ang = this.rng.nextFloat() * Math.PI * 2;
    const pos: Vec3 = {
      x: center.x + Math.sin(ang) * spread,
      y: 0,
      z: center.z + Math.cos(ang) * spread,
    };

    const { mesh, bodyMat } = this.createNpcMesh(enemy);
    this.group.add(mesh);

    const maxHealth = enemy ? 3.0 + this.threat * 0.6 : 1.0;
    const npc: NpcEntity = {
      id: `npc-${this.nextId++}`,
      position: pos,
      target: { ...pos },
      yaw: this.rng.nextFloat() * Math.PI * 2,
      bob: this.rng.nextFloat() * 6,
      speed: enemy ? 2.6 + this.threat * 0.22 : 1.35,
      health: maxHealth,
      maxHealth,
      flash: 0,
      death: 0,
      attackCd: 0,
      active: true,
      enemy,
      met: false,
      panic: 0,
      talk: 0,
      name: enemy
        ? ENEMY_NAMES[idx % ENEMY_NAMES.length]
        : CITIZEN_NAMES[idx % CITIZEN_NAMES.length],
      line: enemy
        ? 'YOU DO NOT BELONG HERE.'
        : CITIZEN_LINES[idx % CITIZEN_LINES.length],
      mesh,
      bodyMat,
    };
    this.npcs.push(npc);
    this.resolveOne(npc, colliders);
  }

  private resolveOne(n: NpcEntity, colliders: Aabb[]): void {
    const r = 0.45;
    for (const b of colliders) {
      if (n.position.y + 1.6 < b.min.y || n.position.y > b.max.y) continue;
      const cx = Math.max(b.min.x, Math.min(b.max.x, n.position.x));
      const cz = Math.max(b.min.z, Math.min(b.max.z, n.position.z));
      const dx = n.position.x - cx;
      const dz = n.position.z - cz;
      const d2 = dx * dx + dz * dz;
      if (d2 > r * r) continue;
      if (d2 > 1e-6) {
        const d = Math.sqrt(d2);
        n.position.x = cx + (dx / d) * r;
        n.position.z = cz + (dz / d) * r;
      } else {
        const pxl = n.position.x - b.min.x + r;
        const pxr = b.max.x - n.position.x + r;
        const pzl = n.position.z - b.min.z + r;
        const pzr = b.max.z - n.position.z + r;
        const m = Math.min(pxl, pxr, pzl, pzr);
        if (m === pxl) n.position.x = b.min.x - r;
        else if (m === pxr) n.position.x = b.max.x + r;
        else if (m === pzl) n.position.z = b.min.z - r;
        else n.position.z = b.max.z + r;
      }
    }
  }

  update(
    dt: number,
    playerPos: Vec3,
    colliders: Aabb[],
    onHitPlayer: (dmg: number) => void,
    onCitizenFall: () => void
  ): void {
    this.reinforceTimer += dt;
    if (this.reinforceTimer >= 12.0) {
      this.reinforceTimer = 0;
      const wantEnemies = 6 + this.threat * 4;
      if (this.aliveEnemies() < wantEnemies) {
        this.spawnOne(true, { x: 0, y: 0, z: 0 }, 95, this.nextId, colliders);
      }
      if (this.aliveCitizens() < 20) {
        this.spawnOne(false, { x: 0, y: 0, z: 0 }, 85, this.nextId, colliders);
      }
    }

    for (const n of this.npcs) {
      if (!n.active) continue;

      if (n.death > 0) {
        n.death += dt * 1.4;
        n.mesh.scale.setScalar(Math.max(0.01, 1 - n.death));
        n.mesh.rotation.x = n.death * 1.5;
        if (n.death >= 1.0) {
          n.active = false;
          n.mesh.visible = false;
        }
        continue;
      }

      n.flash = Math.max(0, n.flash - dt * 4);
      n.attackCd = Math.max(0, n.attackCd - dt);
      n.talk = Math.max(0, n.talk - dt);
      n.panic = Math.max(0, n.panic - dt);
      n.bob += dt * 7.0;

      n.bodyMat.emissiveIntensity = n.flash > 0 ? 2.5 : n.enemy ? 0.55 : 0.2;

      const toPx = playerPos.x - n.position.x;
      const toPz = playerPos.z - n.position.z;
      const dp = Math.hypot(toPx, toPz);

      if (n.enemy) {
        let preyPos = playerPos;
        let best = dp;
        let preyCitizen: NpcEntity | null = null;

        for (const o of this.npcs) {
          if (!o.active || o.enemy || o.death > 0) continue;
          const d = Math.hypot(
            o.position.x - n.position.x,
            o.position.z - n.position.z
          );
          if (d < best && d < 14.0 + this.threat * 2.0) {
            best = d;
            preyPos = o.position;
            preyCitizen = o;
          }
        }

        const aggro = 28.0 + this.threat * 4.0;
        if (best < aggro) {
          n.target = { ...preyPos };
        } else if (
          Math.hypot(n.target.x - n.position.x, n.target.z - n.position.z) < 1.2
        ) {
          n.target = {
            x: n.position.x + (this.rng.nextFloat() - 0.5) * 32,
            y: 0,
            z: n.position.z + (this.rng.nextFloat() - 0.5) * 32,
          };
        }

        if (best < 1.9 && n.attackCd === 0) {
          n.attackCd = this.threat >= 4 ? 0.9 : 1.4;
          if (preyCitizen && best < dp) {
            preyCitizen.death = 0.01;
            this.citizenDeaths++;
            onCitizenFall();
          } else if (dp < 2.2 && Math.abs(playerPos.y - 1.7 - n.position.y) < 2.5) {
            onHitPlayer(8 + this.threat * 2);
          }
        }
      } else {
        // Citizen: flee nearest husk, or wander
        let nearestEnemy: NpcEntity | null = null;
        let nearestEd = 12.0;
        for (const e of this.npcs) {
          if (!e.active || !e.enemy || e.death > 0) continue;
          const d = Math.hypot(
            e.position.x - n.position.x,
            e.position.z - n.position.z
          );
          if (d < nearestEd) {
            nearestEd = d;
            nearestEnemy = e;
          }
        }
        if (nearestEnemy) {
          n.panic = 1.5;
          const fx = n.position.x - nearestEnemy.position.x;
          const fz = n.position.z - nearestEnemy.position.z;
          const fl = Math.max(0.1, Math.hypot(fx, fz));
          n.target = {
            x: n.position.x + (fx / fl) * 14,
            y: 0,
            z: n.position.z + (fz / fl) * 14,
          };
        } else if (
          Math.hypot(n.target.x - n.position.x, n.target.z - n.position.z) < 1.0
        ) {
          n.target = {
            x: Math.max(-95, Math.min(95, n.position.x + (this.rng.nextFloat() - 0.5) * 28)),
            y: 0,
            z: Math.max(-95, Math.min(95, n.position.z + (this.rng.nextFloat() - 0.5) * 28)),
          };
        }
      }

      const tx = n.target.x - n.position.x;
      const tz = n.target.z - n.position.z;
      const td = Math.hypot(tx, tz);
      if (td > 0.25) {
        const moveSpd = n.speed * (n.panic > 0 ? 1.8 : 1.0);
        n.position.x += (tx / td) * moveSpd * dt;
        n.position.z += (tz / td) * moveSpd * dt;
        n.yaw = Math.atan2(tx, -tz);
      }

      this.resolveOne(n, colliders);

      n.mesh.position.set(
        n.position.x,
        n.position.y + Math.abs(Math.sin(n.bob)) * 0.08,
        n.position.z
      );
      n.mesh.rotation.y = -n.yaw;
    }
  }

  nearest(p: Vec3, maxDist: number, enemy: boolean): NpcEntity | null {
    let best: NpcEntity | null = null;
    let bestD = maxDist;
    for (const n of this.npcs) {
      if (!n.active || n.death > 0 || n.enemy !== enemy) continue;
      const d = Math.hypot(n.position.x - p.x, n.position.z - p.z);
      if (d < bestD) {
        bestD = d;
        best = n;
      }
    }
    return best;
  }

  aliveEnemies(): number {
    let c = 0;
    for (const n of this.npcs) if (n.active && n.enemy && n.death === 0) c++;
    return c;
  }

  aliveCitizens(): number {
    let c = 0;
    for (const n of this.npcs) if (n.active && !n.enemy && n.death === 0) c++;
    return c;
  }

  clear(): void {
    for (const n of this.npcs) {
      this.group.remove(n.mesh);
    }
    this.npcs = [];
  }

  dispose(): void {
    this.clear();
    this.sharedGeos.forEach((g) => g.dispose());
    this.sharedMats.forEach((m) => m.dispose());
  }
}
