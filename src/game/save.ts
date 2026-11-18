import type { Dimension, HudSnapshot, SaveGame } from './types';

const STORAGE_KEY = 'webverse-save.txt';

export function createEmptySave(): SaveGame {
  return {
    version: 1,
    valid: 0,
    shards: 0,
    trust: 0,
    kills: 0,
    citySaved: 0,
    dimension: 0,
    level: 1,
    posX: 0,
    posY: 1.7,
    posZ: 104,
    health: 100,
    timePlayed: 0,
  };
}

export function encodeSave(s: SaveGame): string {
  return [
    `version=${s.version}`,
    `valid=${s.valid}`,
    `shards=${s.shards}`,
    `trust=${s.trust}`,
    `kills=${s.kills}`,
    `citySaved=${s.citySaved}`,
    `dimension=${s.dimension}`,
    `level=${s.level}`,
    `posX=${s.posX.toFixed(2)}`,
    `posY=${s.posY.toFixed(2)}`,
    `posZ=${s.posZ.toFixed(2)}`,
    `health=${s.health.toFixed(1)}`,
    `timePlayed=${s.timePlayed.toFixed(1)}`,
  ].join('\n');
}

export function decodeSave(text: string): SaveGame | null {
  try {
    const out = createEmptySave();
    const lines = text.split(/\r?\n/);
    let foundAny = false;
    for (const line of lines) {
      const idx = line.indexOf('=');
      if (idx <= 0) continue;
      const k = line.slice(0, idx).trim();
      const v = line.slice(idx + 1).trim();
      foundAny = true;
      switch (k) {
        case 'version':
          out.version = parseInt(v, 10) || 1;
          break;
        case 'valid':
          out.valid = parseInt(v, 10) || 0;
          break;
        case 'shards':
          out.shards = parseInt(v, 10) || 0;
          break;
        case 'trust':
          out.trust = parseInt(v, 10) || 0;
          break;
        case 'kills':
          out.kills = parseInt(v, 10) || 0;
          break;
        case 'citySaved':
          out.citySaved = parseInt(v, 10) || 0;
          break;
        case 'dimension':
          out.dimension = (parseInt(v, 10) === 1 ? 1 : 0) as Dimension;
          break;
        case 'level':
          out.level = Math.max(1, Math.min(5, parseInt(v, 10) || 1));
          break;
        case 'posX':
          out.posX = parseFloat(v) || 0;
          break;
        case 'posY':
          out.posY = parseFloat(v) || 1.7;
          break;
        case 'posZ':
          out.posZ = parseFloat(v) || 104;
          break;
        case 'health':
          out.health = parseFloat(v) || 100;
          break;
        case 'timePlayed':
          out.timePlayed = parseFloat(v) || 0;
          break;
      }
    }
    return foundAny && out.valid === 1 ? out : null;
  } catch {
    return null;
  }
}

export function loadSaveFromStorage(): SaveGame | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return decodeSave(raw);
  } catch {
    return null;
  }
}

export function writeSaveToStorage(s: SaveGame): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, encodeSave(s));
  } catch {
    // Ignore storage quota / private mode issues
  }
}

export function clearSaveFromStorage(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Ignore
  }
}

export function computeThreatLevel(
  shards: number,
  kills: number,
  citySaved: boolean
): number {
  let tier = 1;
  if (shards >= 4 || kills >= 3) tier = 2;
  if (shards >= 10 || kills >= 8) tier = 3;
  if (shards >= 16 || kills >= 15) tier = 4;
  if (shards >= 22 || kills >= 24 || citySaved) tier = 5;
  return tier;
}

export function buildDiagnosticReport(
  hud: HudSnapshot,
  pos: { x: number; y: number; z: number },
  gpuRenderer: string
): string {
  const saveText = encodeSave({
    version: 1,
    valid: 1,
    shards: hud.shards,
    trust: hud.trust,
    kills: hud.kills,
    citySaved: hud.citySaved ? 1 : 0,
    dimension: hud.dimension,
    level: hud.threatLevel,
    posX: pos.x,
    posY: pos.y,
    posZ: pos.z,
    health: hud.health,
    timePlayed: hud.timePlayed,
  });

  return [
    '=== WEBVERSE DIAGNOSTIC REPORT ===',
    `runtime=WebGL2 / Three.js`,
    `gpu=${gpuRenderer || 'Unknown'}`,
    `mode=${hud.mode}`,
    `dimension=${hud.dimension === 0 ? 'NEON NOIR' : 'INKVERSE'}`,
    `fps=${hud.fps.toFixed(1)}`,
    `pos=(${pos.x.toFixed(2)}, ${pos.y.toFixed(2)}, ${pos.z.toFixed(2)})`,
    `speed=${hud.speed.toFixed(2)} m/s`,
    `threat_tier=${hud.threatLevel}`,
    `shards=${hud.shards}/${hud.totalShards}`,
    `husks_defeated=${hud.kills}`,
    `street_trust=${hud.trust}`,
    `citizens_alive=${hud.aliveCitizens}`,
    '--- webverse-save.txt ---',
    saveText,
  ].join('\n');
}
