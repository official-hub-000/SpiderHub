import { useEffect, useRef, useState } from 'react';
import { GameHud } from './components/GameHud';
import { WebverseEngine } from './game/engine';
import type { HudSnapshot } from './game/types';

const INITIAL_HUD: HudSnapshot = {
  mode: 'title',
  dimension: 0,
  dimensionBanner: '',
  health: 100,
  maxHealth: 100,
  shards: 0,
  totalShards: 24,
  kills: 0,
  aliveHusks: 10,
  aliveCitizens: 26,
  citizenDeaths: 0,
  trust: 0,
  threatLevel: 1,
  citySaved: false,
  timePlayed: 0,
  speed: 0,
  grappling: false,
  grounded: true,
  subtitle: '',
  actTitle: '',
  dialogue: null,
  nearTalkName: null,
  nearPortalDist: 999,
  nearSpireSeal: false,
  movieTake: 1,
  labels: [],
  spireMarker: { x: 0, y: 0, onScreen: false, dist: 104, bearingDeg: 0 },
  fps: 60,
  hasSave: false,
  muted: false,
  pointerLocked: false,
};

export function App() {
  const mountRef = useRef<HTMLDivElement | null>(null);
  const [engine, setEngine] = useState<WebverseEngine | null>(null);
  const [hud, setHud] = useState<HudSnapshot>(INITIAL_HUD);

  useEffect(() => {
    if (!mountRef.current) return;
    const instance = new WebverseEngine(mountRef.current, (snapshot) => {
      setHud(snapshot);
    });
    setEngine(instance);

    return () => {
      instance.dispose();
      setEngine(null);
    };
  }, []);

  return (
    <div className="relative h-screen w-screen overflow-hidden bg-[#080a16]">
      <div ref={mountRef} className="h-full w-full" />
      <GameHud hud={hud} engine={engine} />
    </div>
  );
}

export default App;
