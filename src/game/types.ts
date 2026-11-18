export type GameMode =
  | 'title'
  | 'cutscene'
  | 'playing'
  | 'paused'
  | 'movie'
  | 'ending'
  | 'gameover';

export type Dimension = 0 | 1; // 0 = Neon Noir, 1 = Inkverse

export interface Vec3 {
  x: number;
  y: number;
  z: number;
}

export interface Aabb {
  min: Vec3;
  max: Vec3;
}

export interface InputState {
  moveX: number;
  moveY: number;
  lookDX: number;
  lookDY: number;
  jump: boolean;
  web: boolean;
  attack: boolean;
  talk: boolean;
}

export interface SaveGame {
  version: number;
  valid: number;
  shards: number;
  trust: number;
  kills: number;
  citySaved: number;
  dimension: Dimension;
  level: number;
  posX: number;
  posY: number;
  posZ: number;
  health: number;
  timePlayed: number;
}

export interface ProjectedLabel {
  id: string;
  name: string;
  enemy: boolean;
  x: number;
  y: number;
  dist: number;
  canTalk: boolean;
  healthRatio: number;
}

export interface SpireMarker {
  x: number;
  y: number;
  onScreen: boolean;
  dist: number;
  bearingDeg: number;
}

export interface HudSnapshot {
  mode: GameMode;
  dimension: Dimension;
  dimensionBanner: string;
  health: number;
  maxHealth: number;
  shards: number;
  totalShards: number;
  kills: number;
  aliveHusks: number;
  aliveCitizens: number;
  citizenDeaths: number;
  trust: number;
  threatLevel: number;
  citySaved: boolean;
  timePlayed: number;
  speed: number;
  grappling: boolean;
  grounded: boolean;
  subtitle: string;
  actTitle: string;
  dialogue: { name: string; line: string } | null;
  nearTalkName: string | null;
  nearPortalDist: number;
  nearSpireSeal: boolean;
  movieTake: number;
  labels: ProjectedLabel[];
  spireMarker: SpireMarker;
  fps: number;
  hasSave: boolean;
  muted: boolean;
  pointerLocked: boolean;
}
