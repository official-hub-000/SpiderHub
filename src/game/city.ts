import * as THREE from 'three';
import { Pcg32 } from './rng';
import type { Aabb, Vec3 } from './types';

export interface CityConfig {
  blocksX: number;
  blocksZ: number;
  blockSpacing: number;
  lotSize: number;
  seed: number;
}

export interface CityWorld {
  group: THREE.Group;
  cityMaterial: THREE.ShaderMaterial;
  skyMesh: THREE.Mesh;
  skyMaterial: THREE.ShaderMaterial;
  spirePillar: THREE.Mesh;
  portals: { pos: Vec3; group: THREE.Group; cooldown: number }[];
  shards: { pos: Vec3; mesh: THREE.Mesh; collected: boolean }[];
  colliders: Aabb[];
  spawn: Vec3;
  spire: Vec3;
  extent: number;
  dispose: () => void;
}

interface MeshBuilder {
  positions: number[];
  normals: number[];
  colors: number[];
  flags: number[]; // x=0 (world), y=unlit, z=windows/joints, w=rim
  indices: number[];
}

function appendBox(
  mb: MeshBuilder,
  cx: number,
  cy: number,
  cz: number,
  hx: number,
  hy: number,
  hz: number,
  r: number,
  g: number,
  b: number,
  unlit = 0,
  windows = 1,
  rim = 0
): void {
  const x0 = cx - hx,
    x1 = cx + hx;
  const y0 = cy - hy,
    y1 = cy + hy;
  const z0 = cz - hz,
    z1 = cz + hz;

  const faces: {
    verts: [number, number, number][];
    n: [number, number, number];
  }[] = [
    {
      // +Z
      verts: [
        [x0, y0, z1],
        [x1, y0, z1],
        [x1, y1, z1],
        [x0, y1, z1],
      ],
      n: [0, 0, 1],
    },
    {
      // -Z
      verts: [
        [x1, y0, z0],
        [x0, y0, z0],
        [x0, y1, z0],
        [x1, y1, z0],
      ],
      n: [0, 0, -1],
    },
    {
      // +X
      verts: [
        [x1, y0, z1],
        [x1, y0, z0],
        [x1, y1, z0],
        [x1, y1, z1],
      ],
      n: [1, 0, 0],
    },
    {
      // -X
      verts: [
        [x0, y0, z0],
        [x0, y0, z1],
        [x0, y1, z1],
        [x0, y1, z0],
      ],
      n: [-1, 0, 0],
    },
    {
      // +Y
      verts: [
        [x0, y1, z1],
        [x1, y1, z1],
        [x1, y1, z0],
        [x0, y1, z0],
      ],
      n: [0, 1, 0],
    },
    {
      // -Y
      verts: [
        [x0, y0, z0],
        [x1, y0, z0],
        [x1, y0, z1],
        [x0, y0, z1],
      ],
      n: [0, -1, 0],
    },
  ];

  for (const f of faces) {
    const base = mb.positions.length / 3;
    for (const v of f.verts) {
      mb.positions.push(v[0], v[1], v[2]);
      mb.normals.push(f.n[0], f.n[1], f.n[2]);
      mb.colors.push(r, g, b);
      mb.flags.push(0, unlit, windows, rim);
    }
    mb.indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
  }
}

function buildTower(
  mb: MeshBuilder,
  colliders: Aabb[],
  rng: Pcg32,
  ox: number,
  oz: number,
  w: number,
  d: number,
  h: number,
  base: [number, number, number],
  accent: [number, number, number]
): void {
  const tiers = 1 + rng.nextBounded(3);
  let y = 0;
  let cw = w;
  let cd = d;

  for (let t = 0; t < tiers; t++) {
    const th =
      t === tiers - 1 ? h - y : h * (0.3 + rng.nextFloat() * 0.28);
    if (th < 2.0) break;
    const shade = 0.68 + 0.32 * (t / Math.max(1, tiers - 1));
    appendBox(
      mb,
      ox,
      y + th * 0.5,
      oz,
      cw * 0.5,
      th * 0.5,
      cd * 0.5,
      base[0] * shade,
      base[1] * shade,
      base[2] * shade,
      0,
      1,
      0.15
    );
    colliders.push({
      min: { x: ox - cw * 0.5, y, z: oz - cd * 0.5 },
      max: { x: ox + cw * 0.5, y: y + th, z: oz + cd * 0.5 },
    });
    y += th;
    cw *= 0.7;
    cd *= 0.7;
  }

  // Rooftop trim
  appendBox(
    mb,
    ox,
    y + 0.35,
    oz,
    cw * 0.5 + 0.45,
    0.35,
    cd * 0.5 + 0.45,
    accent[0],
    accent[1],
    accent[2],
    0,
    0,
    0.3
  );

  // Antenna / spire mast
  if (rng.nextFloat() < 0.45) {
    appendBox(
      mb,
      ox + cw * 0.22,
      y + 3.2,
      oz + cd * 0.22,
      0.11,
      3.2,
      0.11,
      0.3,
      0.32,
      0.38,
      0,
      0,
      0
    );
    appendBox(
      mb,
      ox + cw * 0.22,
      y + 6.6,
      oz + cd * 0.22,
      0.22,
      0.22,
      0.22,
      1.0,
      0.25,
      0.3,
      1,
      0,
      0
    );
  }

  // Lit billboard strip
  if (rng.nextFloat() < 0.4) {
    const bw = cw * (0.5 + rng.nextFloat() * 0.4);
    appendBox(
      mb,
      ox,
      y * 0.55,
      oz + cd * 0.5 + 0.12,
      bw * 0.5,
      1.1,
      0.12,
      accent[0] * 1.25,
      accent[1] * 1.25,
      accent[2] * 1.25,
      1,
      0,
      0
    );
  }

  // Ground-floor shopfront glow
  appendBox(
    mb,
    ox,
    0.6,
    oz + cd * 0.5 + 0.08,
    cw * 0.42,
    0.55,
    0.08,
    1.0,
    0.85,
    0.55,
    1,
    0,
    0
  );
}

export function createCityWorld(
  cfg: CityConfig = {
    blocksX: 8,
    blocksZ: 8,
    blockSpacing: 26.0,
    lotSize: 15.0,
    seed: 12345,
  }
): CityWorld {
  const rng = new Pcg32(cfg.seed);
  const mb: MeshBuilder = {
    positions: [],
    normals: [],
    colors: [],
    flags: [],
    indices: [],
  };
  const colliders: Aabb[] = [];
  const portalPositions: Vec3[] = [];

  const halfX = (cfg.blocksX - 1) * 0.5 * cfg.blockSpacing;
  const halfZ = (cfg.blocksZ - 1) * 0.5 * cfg.blockSpacing;
  const groundExtent = Math.max(halfX, halfZ) + cfg.blockSpacing;

  // Ground asphalt base
  appendBox(
    mb,
    0,
    -0.25,
    0,
    groundExtent * 1.4,
    0.25,
    groundExtent * 1.4,
    0.07,
    0.076,
    0.105,
    0,
    1,
    0
  );

  // Roads
  for (let i = 0; i <= cfg.blocksX; i++) {
    const wx = -halfX - cfg.blockSpacing * 0.5 + i * cfg.blockSpacing;
    appendBox(
      mb,
      wx,
      0.015,
      0,
      cfg.blockSpacing * 0.36,
      0.015,
      groundExtent,
      0.105,
      0.112,
      0.15,
      0,
      1,
      0
    );
  }
  for (let i = 0; i <= cfg.blocksZ; i++) {
    const wz = -halfZ - cfg.blockSpacing * 0.5 + i * cfg.blockSpacing;
    appendBox(
      mb,
      0,
      0.015,
      wz,
      groundExtent,
      0.015,
      cfg.blockSpacing * 0.36,
      0.105,
      0.112,
      0.15,
      0,
      1,
      0
    );
  }

  // Dashed yellow lane markings
  for (let i = 0; i <= cfg.blocksX; i++) {
    const wx = -halfX - cfg.blockSpacing * 0.5 + i * cfg.blockSpacing;
    for (let s = -groundExtent; s < groundExtent; s += 7.0) {
      appendBox(mb, wx, 0.035, s, 0.16, 0.02, 1.6, 0.9, 0.72, 0.24, 0, 0, 0);
    }
  }
  for (let i = 0; i <= cfg.blocksZ; i++) {
    const wz = -halfZ - cfg.blockSpacing * 0.5 + i * cfg.blockSpacing;
    for (let s = -groundExtent; s < groundExtent; s += 7.0) {
      appendBox(mb, s, 0.035, wz, 1.6, 0.02, 0.16, 0.9, 0.72, 0.24, 0, 0, 0);
    }
  }

  const pal: [number, number, number][][] = [
    [
      [0.13, 0.17, 0.34],
      [0.35, 0.62, 1.0],
      [0.95, 0.74, 0.34],
    ],
    [
      [0.34, 0.1, 0.26],
      [1.0, 0.62, 0.12],
      [0.1, 0.86, 0.95],
    ],
    [
      [0.28, 0.15, 0.09],
      [0.92, 0.55, 0.2],
      [0.38, 0.45, 0.52],
    ],
    [
      [0.09, 0.3, 0.24],
      [0.42, 0.95, 0.62],
      [0.48, 0.26, 0.78],
    ],
  ];
  const hMin = [10.0, 22.0, 7.0, 14.0];
  const hVar = [26.0, 30.0, 16.0, 24.0];
  const landmarks = [
    [1, 1],
    [6, 2],
    [3, 6],
  ];

  for (let bz = 0; bz < cfg.blocksZ; bz++) {
    for (let bx = 0; bx < cfg.blocksX; bx++) {
      const cx = -halfX + bx * cfg.blockSpacing;
      const cz = -halfZ + bz * cfg.blockSpacing;
      const district =
        (bx < cfg.blocksX / 2 ? 0 : 1) + (bz < cfg.blocksZ / 2 ? 0 : 2);

      // Raised sidewalk slab
      appendBox(
        mb,
        cx,
        0.06,
        cz,
        cfg.blockSpacing * 0.5 - 1.0,
        0.06,
        cfg.blockSpacing * 0.5 - 1.0,
        0.15,
        0.16,
        0.205,
        0,
        1,
        0
      );

      if (rng.nextFloat() < 0.16) {
        // Park block
        appendBox(
          mb,
          cx,
          0.14,
          cz,
          cfg.blockSpacing * 0.4,
          0.08,
          cfg.blockSpacing * 0.4,
          0.09,
          0.24,
          0.13,
          0,
          0,
          0
        );
        const trees = 4 + rng.nextBounded(5);
        for (let t = 0; t < trees; t++) {
          const tx = cx + (rng.nextFloat() - 0.5) * cfg.blockSpacing * 0.62;
          const tz = cz + (rng.nextFloat() - 0.5) * cfg.blockSpacing * 0.62;
          const th = 2.0 + rng.nextFloat() * 2.5;
          appendBox(
            mb,
            tx,
            0.2 + th * 0.5,
            tz,
            0.14,
            th * 0.5,
            0.14,
            0.16,
            0.11,
            0.07,
            0,
            0,
            0
          );
          appendBox(
            mb,
            tx,
            0.2 + th + 1.1,
            tz,
            1.0 + rng.nextFloat(),
            1.1,
            1.0 + rng.nextFloat(),
            0.13,
            0.38,
            0.18,
            0,
            0,
            0
          );
        }
        continue;
      }

      const isLandmark = landmarks.some(([lx, lz]) => lx === bx && lz === bz);
      if (isLandmark) {
        const h = 68.0 + rng.nextFloat() * 26.0;
        buildTower(
          mb,
          colliders,
          rng,
          cx,
          cz,
          9.0,
          9.0,
          h,
          pal[district][0],
          pal[district][1]
        );
        if ((bx + bz) % 3 === 0) {
          portalPositions.push({
            x: cx + cfg.blockSpacing * 0.5,
            y: 3.0,
            z: cz + cfg.blockSpacing * 0.5,
          });
        }
        continue;
      }

      const n = 1 + rng.nextBounded(2);
      for (let k = 0; k < n; k++) {
        const shape = rng.nextFloat();
        let w: number, d: number;
        if (shape < 0.3) {
          w = cfg.lotSize * (0.75 + rng.nextFloat() * 0.25);
          d = 4.0 + rng.nextFloat() * 4.0;
        } else if (shape < 0.55) {
          w = 3.5 + rng.nextFloat() * 3.0;
          d = 3.5 + rng.nextFloat() * 3.0;
        } else {
          w = 5.0 + rng.nextFloat() * (cfg.lotSize - 5.0);
          d = 5.0 + rng.nextFloat() * (cfg.lotSize - 5.0);
        }
        const h = hMin[district] + rng.nextFloat() * hVar[district];
        const ox =
          cx + (rng.nextFloat() - 0.5) * (cfg.blockSpacing - cfg.lotSize - 2.0);
        const oz =
          cz + (rng.nextFloat() - 0.5) * (cfg.blockSpacing - cfg.lotSize - 2.0);
        const bCol = pal[district][rng.nextBounded(3)];
        const tint = 0.78 + rng.nextFloat() * 0.44;
        const accent = pal[district][rng.nextBounded(3)];
        buildTower(
          mb,
          colliders,
          rng,
          ox,
          oz,
          w,
          d,
          h,
          [bCol[0] * tint, bCol[1] * tint, bCol[2] * tint],
          accent
        );
      }

      if ((bx + bz) % 3 === 0) {
        portalPositions.push({
          x: cx + cfg.blockSpacing * 0.5,
          y: 3.0,
          z: cz + cfg.blockSpacing * 0.5,
        });
      }
    }
  }

  // The Spire at (0, 0, 0)
  appendBox(mb, 0, 45.0, 0, 4.5, 45.0, 4.5, 0.85, 0.2, 0.35, 0, 1, 0.45);
  appendBox(mb, 0, 78.0, 0, 3.0, 8.0, 3.0, 1.0, 0.7, 0.2, 1, 0, 0.5);
  appendBox(mb, 0, 92.0, 0, 5.5, 3.0, 5.5, 1.0, 0.85, 0.4, 0, 0, 0.5);
  appendBox(mb, 0, 98.0, 0, 0.7, 2.5, 0.7, 0.4, 0.95, 1.0, 1, 0, 0.6);
  colliders.push({
    min: { x: -4.5, y: 0, z: -4.5 },
    max: { x: 4.5, y: 95.0, z: 4.5 },
  });

  // Street lamps
  for (let ix = 0; ix <= cfg.blocksX; ix++) {
    const wx = -halfX - cfg.blockSpacing * 0.5 + ix * cfg.blockSpacing;
    for (let iz = 0; iz <= cfg.blocksZ; iz++) {
      const wz = -halfZ - cfg.blockSpacing * 0.5 + iz * cfg.blockSpacing;
      appendBox(mb, wx + 2.6, 2.1, wz + 2.6, 0.06, 2.1, 0.06, 0.24, 0.24, 0.3, 0, 0, 0);
      appendBox(mb, wx + 2.6, 4.3, wz + 2.6, 0.22, 0.22, 0.22, 1.0, 0.85, 0.45, 1, 0, 0);
    }
  }

  // Remove portals inside buildings/Spire
  const validPortals = portalPositions.filter(
    (p) =>
      !colliders.some(
        (b) =>
          p.x > b.min.x - 1.5 &&
          p.x < b.max.x + 1.5 &&
          p.y > b.min.y - 1.5 &&
          p.y < b.max.y + 1.5 &&
          p.z > b.min.z - 1.5 &&
          p.z < b.max.z + 1.5
      )
  );

  // Ensure a starter portal right near spawn so player can immediately see/use one
  const spawnPos: Vec3 = {
    x: 0,
    y: 1.7,
    z: halfZ + cfg.blockSpacing * 0.5,
  };
  validPortals.push({ x: -13.0, y: 3.0, z: spawnPos.z - 13.0 });

  // 24 Collectible Data Shards
  const candShards: Vec3[] = [];
  for (let gx = -halfX; gx <= halfX; gx += cfg.blockSpacing * 0.5) {
    for (let gz = -halfZ; gz <= halfZ; gz += cfg.blockSpacing * 0.5) {
      const p: Vec3 = {
        x: gx + (rng.nextFloat() - 0.5) * 6.0,
        y: 2.2 + rng.nextFloat() * 8.0,
        z: gz + (rng.nextFloat() - 0.5) * 6.0,
      };
      const inside = colliders.some(
        (b) =>
          p.x > b.min.x - 1.0 &&
          p.x < b.max.x + 1.0 &&
          p.y > b.min.y - 1.0 &&
          p.y < b.max.y + 1.0 &&
          p.z > b.min.z - 1.0 &&
          p.z < b.max.z + 1.0
      );
      if (!inside) candShards.push(p);
    }
  }

  const shardPositions: Vec3[] = [];
  if (candShards.length > 0) {
    const want = 24;
    const step = Math.max(1, Math.floor(candShards.length / want));
    for (let i = 0; i < candShards.length && shardPositions.length < want; i += step) {
      shardPositions.push(candShards[i]);
    }
  }

  // Build Three.js BufferGeometry for city
  const cityGeo = new THREE.BufferGeometry();
  cityGeo.setAttribute(
    'position',
    new THREE.Float32BufferAttribute(mb.positions, 3)
  );
  cityGeo.setAttribute(
    'normal',
    new THREE.Float32BufferAttribute(mb.normals, 3)
  );
  cityGeo.setAttribute('color', new THREE.Float32BufferAttribute(mb.colors, 3));
  cityGeo.setAttribute('aFlag', new THREE.Float32BufferAttribute(mb.flags, 4));
  cityGeo.setIndex(mb.indices);
  cityGeo.computeBoundingSphere();

  const cityMaterial = new THREE.ShaderMaterial({
    uniforms: {
      uLightDir: { value: new THREE.Vector3(0.55, 0.72, 0.42).normalize() },
      uLightColor: { value: new THREE.Vector3(0.95, 0.88, 1.0) },
      uAmbient: { value: new THREE.Vector3(0.24, 0.27, 0.38) },
      uFogColor: { value: new THREE.Vector3(0.1, 0.12, 0.24) },
      uComic: { value: 0.0 },
      uTime: { value: 0.0 },
    },
    vertexShader: `
      attribute vec3 color;
      attribute vec4 aFlag;
      varying vec3 vNormal;
      varying vec3 vColor;
      varying vec3 vWorld;
      varying vec4 vFlags;
      void main() {
        vNormal = normalize((modelMatrix * vec4(normal, 0.0)).xyz);
        vColor = color;
        vec4 worldPos = modelMatrix * vec4(position, 1.0);
        vWorld = worldPos.xyz;
        vFlags = aFlag;
        gl_Position = projectionMatrix * viewMatrix * worldPos;
      }
    `,
    fragmentShader: `
      uniform vec3 uLightDir;
      uniform vec3 uLightColor;
      uniform vec3 uAmbient;
      uniform vec3 uFogColor;
      uniform float uComic;
      uniform float uTime;
      varying vec3 vNormal;
      varying vec3 vColor;
      varying vec3 vWorld;
      varying vec4 vFlags;

      void main() {
        if (vFlags.y > 0.5) {
          vec3 unlit = vColor;
          if (uComic > 0.5) {
            float lum = dot(unlit, vec3(0.299, 0.587, 0.114));
            float q = floor(lum * 3.0 + 0.5) / 3.0;
            unlit = mix(unlit, vec3(q), 0.55);
            vec2 fc = gl_FragCoord.xy;
            float dots = sin(fc.x * 0.60) * sin(fc.y * 0.60);
            unlit *= 0.86 + 0.14 * dots;
          }
          gl_FragColor = vec4(unlit, 1.0);
          return;
        }

        vec3 n = normalize(vNormal);
        vec3 l = normalize(uLightDir);
        float diff = max(dot(n, l), 0.0);
        float fill = max(dot(n, -l), 0.0) * 0.32;
        vec3 col = vColor * (uAmbient + uLightColor * (diff + fill));

        float dist = length(vWorld - cameraPosition);
        if (n.y < 0.5) {
          float hgt = clamp(vWorld.y / 34.0, 0.0, 1.0);
          col *= 0.74 + 0.26 * hgt;
        } else {
          col *= 1.12;
        }

        // Ground & roof expansion joints
        if (n.y > 0.5 && vFlags.z > 0.5) {
          vec2 g = abs(fract(vWorld.xz / 2.0) - 0.5);
          float joint = smoothstep(0.465, 0.5, max(g.x, g.y));
          col *= 1.0 - joint * 0.28 * exp(-dist * 0.012);
        }

        // Procedural lit windows on building walls
        if (vFlags.z > 0.5 && n.y < 0.5 && vWorld.y > 2.0) {
          vec2 wuv = (abs(n.x) > 0.5) ? vec2(vWorld.z, vWorld.y) : vec2(vWorld.x, vWorld.y);
          vec2 gpos = wuv * vec2(3.4, 1.15);
          vec2 cid = floor(gpos);
          vec2 cell = fract(gpos);
          float h = fract(sin(dot(cid, vec2(12.9898, 78.233))) * 43758.5453);
          float win = step(0.55, cell.x) * step(0.30, cell.y) * step(cell.y, 0.75);
          win *= step(0.45, h);
          float fade = exp(-dist * 0.018);
          float flick = 0.68 + 0.32 * sin(vWorld.x * 3.1 + vWorld.z * 2.3 + h * 6.28 + uTime * 0.35);
          vec3 winCol = mix(vec3(1.0, 0.80, 0.45), vec3(0.35, 0.92, 1.0), step(0.84, h));
          col += winCol * win * 0.58 * flick * fade;
        }

        // Rim light
        vec3 vdir = normalize(cameraPosition - vWorld);
        float rim = pow(1.0 - max(dot(n, vdir), 0.0), 3.0);
        col += vec3(0.45, 0.75, 1.0) * rim * (0.16 + vFlags.w * 0.85);

        // Distance aerial perspective
        float fog = clamp((dist - 25.0) / 245.0, 0.0, 1.0);
        col = mix(col, uFogColor, fog * 0.88);

        // Inkverse dimension comic shader
        if (uComic > 0.5) {
          float lum = dot(col, vec3(0.299, 0.587, 0.114));
          float q = floor(lum * 3.0 + 0.5) / 3.0;
          col = mix(col, vec3(q), 0.62);
          vec2 fc = gl_FragCoord.xy;
          float dots = sin(fc.x * 0.60) * sin(fc.y * 0.60);
          col *= 0.86 + 0.14 * dots;
          float ndv = clamp(dot(n, vdir), 0.0, 1.0);
          col *= 1.0 - smoothstep(0.32, 0.0, ndv) * 0.55;
        }

        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });

  const group = new THREE.Group();
  const cityMesh = new THREE.Mesh(cityGeo, cityMaterial);
  cityMesh.frustumCulled = false;
  group.add(cityMesh);

  // Sky Dome matching sky.frag (stars + moon + Neon Noir / Inkverse sky)
  const skyGeo = new THREE.SphereGeometry(520, 32, 24);
  const skyMaterial = new THREE.ShaderMaterial({
    side: THREE.BackSide,
    depthWrite: false,
    uniforms: {
      uTime: { value: 0 },
      uInk: { value: 0 },
    },
    vertexShader: `
      varying vec3 vDir;
      void main() {
        vDir = position;
        gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
    fragmentShader: `
      uniform float uTime;
      uniform float uInk;
      varying vec3 vDir;
      const float PI = 3.14159265358979323846;

      vec3 ramp(float t) {
        vec3 zenith  = vec3(0.045, 0.065, 0.200);
        vec3 mid     = vec3(0.150, 0.215, 0.470);
        vec3 horizon = vec3(0.760, 0.470, 0.330);
        vec3 nadir   = vec3(0.070, 0.075, 0.150);
        if (t < 0.42) return mix(zenith, mid, t / 0.42);
        if (t < 0.58) return mix(mid, horizon, (t - 0.42) / 0.16);
        return mix(horizon, nadir, (t - 0.58) / 0.42);
      }

      float hash13(vec3 p) {
        p = fract(p * 0.3183099 + vec3(0.103, 0.113, 0.123));
        p *= 17.0;
        return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
      }

      void main() {
        vec3 dir = normalize(vDir);
        float t = acos(clamp(dir.y, -1.0, 1.0)) / PI;
        vec3 col = ramp(t);
        if (uInk > 0.5) {
          col *= vec3(0.55, 0.20, 0.48);
        }

        if (dir.y > 0.015) {
          vec3 sp = dir * 90.0;
          vec3 cell = floor(sp);
          float h = hash13(cell);
          vec3 f = fract(sp) - 0.5;
          float d = length(f);
          float body = 1.0 - smoothstep(0.02, 0.22, d);
          float on = smoothstep(0.955, 0.995, h);
          float mag = 0.35 + 0.65 * hash13(cell + 7.31);
          float tw = 0.70 + 0.30 * sin(uTime * 1.7 + h * 41.0);
          float fade = smoothstep(0.015, 0.18, dir.y) * (1.0 - uInk * 0.55);
          col += vec3(0.82, 0.88, 1.00) * (body * on * mag * tw * 0.85) * fade;
        }

        vec3 md = normalize(vec3(0.44, 0.36, -0.82));
        float mdd = dot(dir, md);
        float disc = smoothstep(0.99880, 0.99955, mdd);
        float halo = pow(clamp(mdd, 0.0, 1.0), 160.0) * 0.35;
        float sky = smoothstep(0.02, 0.22, dir.y);
        vec3 moonCol = mix(vec3(0.92, 0.95, 1.00), vec3(1.00, 0.72, 0.95), uInk);
        col += moonCol * (disc * 1.35 + halo) * sky;
        col -= moonCol * disc * 0.12 * smoothstep(0.15, 0.55, hash13(floor(dir * 260.0))) * sky;

        gl_FragColor = vec4(col, 1.0);
      }
    `,
  });
  const skyMesh = new THREE.Mesh(skyGeo, skyMaterial);
  skyMesh.frustumCulled = false;
  group.add(skyMesh);

  // Pulsing light pillar over the Spire
  const pillarGeo = new THREE.CylinderGeometry(1.1, 2.2, 220, 16, 1, true);
  const pillarMat = new THREE.MeshBasicMaterial({
    color: 0x38bdf8,
    transparent: true,
    opacity: 0.22,
    side: THREE.DoubleSide,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  });
  const spirePillar = new THREE.Mesh(pillarGeo, pillarMat);
  spirePillar.position.set(0, 145, 0);
  group.add(spirePillar);

  // Dimension Portals
  const portalOuterGeo = new THREE.TorusGeometry(2.6, 0.22, 12, 32);
  const portalInnerGeo = new THREE.TorusGeometry(1.9, 0.12, 10, 28);
  const portalCoreGeo = new THREE.CircleGeometry(1.8, 24);
  const portalOuterMat = new THREE.MeshBasicMaterial({ color: 0xa855f7 });
  const portalInnerMat = new THREE.MeshBasicMaterial({ color: 0x22d3ee });
  const portalCoreMat = new THREE.MeshBasicMaterial({
    color: 0x7c3aed,
    transparent: true,
    opacity: 0.45,
    side: THREE.DoubleSide,
    depthWrite: false,
  });

  const portals = validPortals.map((pos) => {
    const pg = new THREE.Group();
    pg.position.set(pos.x, pos.y, pos.z);
    const outer = new THREE.Mesh(portalOuterGeo, portalOuterMat);
    const inner = new THREE.Mesh(portalInnerGeo, portalInnerMat);
    const core = new THREE.Mesh(portalCoreGeo, portalCoreMat);
    pg.add(outer, inner, core);
    group.add(pg);
    return { pos, group: pg, cooldown: 0 };
  });

  // 24 Data Shards
  const shardGeo = new THREE.OctahedronGeometry(0.65, 0);
  const shardMat = new THREE.MeshStandardMaterial({
    color: 0x22d3ee,
    emissive: 0x06b6d4,
    emissiveIntensity: 1.6,
    roughness: 0.15,
    metalness: 0.85,
  });
  const shards = shardPositions.map((pos) => {
    const mesh = new THREE.Mesh(shardGeo, shardMat);
    mesh.position.set(pos.x, pos.y, pos.z);
    group.add(mesh);
    return { pos, mesh, collected: false };
  });

  const dispose = () => {
    cityGeo.dispose();
    cityMaterial.dispose();
    skyGeo.dispose();
    skyMaterial.dispose();
    pillarGeo.dispose();
    pillarMat.dispose();
    portalOuterGeo.dispose();
    portalInnerGeo.dispose();
    portalCoreGeo.dispose();
    portalOuterMat.dispose();
    portalInnerMat.dispose();
    portalCoreMat.dispose();
    shardGeo.dispose();
    shardMat.dispose();
  };

  return {
    group,
    cityMaterial,
    skyMesh,
    skyMaterial,
    spirePillar,
    portals,
    shards,
    colliders,
    spawn: spawnPos,
    spire: { x: 0, y: 95, z: 0 },
    extent: groundExtent,
    dispose,
  };
}
