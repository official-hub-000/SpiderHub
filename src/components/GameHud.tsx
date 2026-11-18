import React, { useState } from 'react';
import {
  Volume2,
  VolumeX,
  Pause,
  Play,
  Film,
  HelpCircle,
  RotateCcw,
  Save,
  LogOut,
  Sparkles,
  Shield,
  Crosshair,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import type { WebverseEngine } from '../game/engine';
import type { HudSnapshot } from '../game/types';

interface GameHudProps {
  hud: HudSnapshot;
  engine: WebverseEngine | null;
}

export const GameHud: React.FC<GameHudProps> = ({ hud, engine }) => {
  const [reportText, setReportText] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [controlsCollapsed, setControlsCollapsed] = useState(false);

  const openReport = () => {
    if (!engine) return;
    setReportText(engine.getDiagnosticReport());
    setCopied(false);
  };

  const copyReport = async () => {
    if (!reportText) return;
    try {
      await navigator.clipboard.writeText(reportText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard might be blocked inside iframe; text remains selectable in modal
    }
  };

  return (
    <div className="pointer-events-none fixed inset-0 z-10 flex flex-col justify-between">
      {/* === TITLE SCREEN === */}
      {hud.mode === 'title' && (
        <div className="pointer-events-auto flex h-full w-full flex-col justify-between p-6 md:p-10">
          <div className="flex items-start justify-between">
            <div className="border border-cyan-400/40 bg-[#080c1c]/88 px-6 py-5 shadow-2xl max-w-lg">
              <div className="text-[11px] tracking-[0.28em] text-cyan-300 uppercase">
                SPIDERHUB · NEXUS-9 · 2147
              </div>
              <h1 className="mt-1 text-3xl md:text-4xl font-black tracking-tight text-white">
                WEBVERSE: MULTIVERSE
              </h1>
              <p className="mt-2 text-xs leading-relaxed text-slate-300">
                Procedural 3D web-swinging action across Neon Noir and Inkverse.
                Swing through NEXUS-9, fight Husks, collect 24 data shards, and
                reach the Spire to seal the bleed.
              </p>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => engine?.toggleMute()}
                className="flex items-center gap-1.5 border border-slate-600/70 bg-[#080c1c]/88 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-cyan-400 hover:text-white cursor-pointer"
                title="Toggle Audio"
              >
                {hud.muted ? <VolumeX size={15} /> : <Volume2 size={15} />}
                <span>{hud.muted ? 'MUTED' : 'AUDIO'}</span>
              </button>
              <button
                onClick={openReport}
                className="flex items-center gap-1.5 border border-slate-600/70 bg-[#080c1c]/88 px-3 py-2 text-xs font-semibold text-slate-200 hover:border-cyan-400 hover:text-white cursor-pointer"
                title="Diagnostic Report"
              >
                <HelpCircle size={15} />
                <span>?</span>
              </button>
            </div>
          </div>

          <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              {hud.hasSave && (
                <button
                  onClick={() => engine?.continueGame()}
                  className="flex items-center gap-2 border border-cyan-400 bg-cyan-500/90 px-5 py-3 text-xs font-bold tracking-wider text-slate-950 hover:bg-cyan-400 cursor-pointer"
                >
                  <Play size={15} />
                  CONTINUE
                </button>
              )}
              <button
                onClick={() => engine?.startNewGame()}
                className="flex items-center gap-2 border border-rose-400/80 bg-rose-600/90 px-5 py-3 text-xs font-bold tracking-wider text-white hover:bg-rose-500 cursor-pointer"
              >
                <Play size={15} />
                {hud.hasSave ? 'NEW GAME' : 'ENTER NEXUS-9'}
              </button>
              <button
                onClick={() => engine?.startMovie()}
                className="flex items-center gap-2 border border-purple-400/70 bg-[#120d26]/90 px-5 py-3 text-xs font-bold tracking-wider text-purple-200 hover:border-purple-300 hover:text-white cursor-pointer"
              >
                <Film size={15} />
                MOVIE (TAKE {hud.movieTake === 1 ? '1: NOIR' : '2: INK'})
              </button>
            </div>

            <div className="border border-slate-700/70 bg-[#080c1c]/88 px-4 py-3 text-[11px] text-slate-300">
              <div className="font-bold text-cyan-300 mb-1 tracking-wider">
                CONTROLS
              </div>
              <div className="grid grid-cols-2 gap-x-5 gap-y-0.5">
                <span>WASD / Arrows — Move</span>
                <span>Mouse Drag / Lock — Look</span>
                <span>Shift / Q — Web Swing</span>
                <span>Space — Jump</span>
                <span>F / Right-Click — Attack</span>
                <span>E — Talk / Seal Spire</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* === CUTSCENE / MOVIE / ENDING LETTERBOX === */}
      {(hud.mode === 'cutscene' ||
        hud.mode === 'movie' ||
        hud.mode === 'ending') && (
        <div className="pointer-events-auto flex h-full w-full flex-col justify-between">
          <div className="flex items-center justify-between bg-black/95 px-6 py-4 border-b border-slate-800">
            <div className="text-xs font-bold tracking-[0.25em] text-cyan-300">
              {hud.actTitle}
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => engine?.toggleMute()}
                className="border border-slate-700 bg-slate-900 px-3 py-1.5 text-xs text-slate-200 hover:border-cyan-400 cursor-pointer"
              >
                {hud.muted ? 'UNMUTE' : 'MUTE'}
              </button>
              <button
                onClick={() => engine?.skipSequence()}
                className="border border-cyan-400/70 bg-cyan-500/20 px-4 py-1.5 text-xs font-bold tracking-wider text-cyan-200 hover:bg-cyan-500/35 cursor-pointer"
              >
                SKIP [ESC]
              </button>
            </div>
          </div>

          <div className="flex items-center justify-center bg-black/95 px-6 py-6 border-t border-slate-800 min-h-[84px]">
            <p className="text-center text-sm md:text-base font-bold tracking-widest text-white">
              {hud.subtitle}
            </p>
          </div>
        </div>
      )}

      {/* === ACTIVE PLAYING HUD === */}
      {hud.mode === 'playing' && (
        <>
          {/* Projected 3D labels (NPC names & HP) */}
          <div className="pointer-events-none fixed inset-0 overflow-hidden">
            {hud.labels.map((lbl) => (
              <div
                key={lbl.id}
                style={{
                  transform: `translate(${lbl.x}px, ${lbl.y}px) translate(-50%, -100%)`,
                }}
                className="absolute left-0 top-0 flex flex-col items-center"
              >
                <span
                  className={`px-1.5 py-0.5 text-[10px] font-bold tracking-wider border ${
                    lbl.enemy
                      ? 'bg-rose-950/90 border-rose-500/70 text-rose-200'
                      : 'bg-slate-950/85 border-cyan-500/50 text-cyan-200'
                  }`}
                >
                  {lbl.name}
                  {lbl.canTalk ? ' [E: TALK]' : ''}
                </span>
                {lbl.enemy && lbl.healthRatio < 1 && (
                  <div className="mt-0.5 h-1 w-10 bg-slate-900 border border-rose-500/50">
                    <div
                      className="h-full bg-rose-500"
                      style={{ width: `${Math.max(0, lbl.healthRatio * 100)}%` }}
                    />
                  </div>
                )}
              </div>
            ))}

            {/* Objective SPIRE marker */}
            <div
              style={{
                transform: `translate(${hud.spireMarker.x}px, ${hud.spireMarker.y}px) translate(-50%, -50%)`,
              }}
              className="absolute left-0 top-0 flex flex-col items-center"
            >
              <div className="border border-amber-400/90 bg-[#1a1105]/85 px-2 py-0.5 text-[10px] font-bold tracking-wider text-amber-300">
                SPIRE {hud.spireMarker.dist}M
              </div>
            </div>
          </div>

          {/* Top Bar */}
          <div className="flex items-start justify-between p-4">
            {/* Compact Top-Left Status Panel */}
            <div className="pointer-events-auto border border-slate-700/80 bg-[#080c1c]/88 px-3.5 py-2.5 shadow-lg min-w-[240px]">
              <div className="flex items-center justify-between gap-3">
                <span
                  className={`px-1.5 py-0.5 text-[10px] font-bold tracking-widest border ${
                    hud.dimension === 1
                      ? 'border-fuchsia-400 bg-fuchsia-950/90 text-fuchsia-200'
                      : 'border-cyan-400 bg-cyan-950/90 text-cyan-200'
                  }`}
                >
                  {hud.dimension === 1 ? 'INKVERSE' : 'NEON NOIR'}
                </span>
                <span className="text-[10px] font-bold tracking-wider text-amber-300">
                  THREAT TIER {hud.threatLevel}
                </span>
              </div>

              {/* Health Bar */}
              <div className="mt-2">
                <div className="flex justify-between text-[10px] text-slate-300 mb-0.5">
                  <span>KAGE HP</span>
                  <span>{Math.ceil(hud.health)} / {hud.maxHealth}</span>
                </div>
                <div className="h-2 w-full bg-slate-900 border border-slate-700">
                  <div
                    className={`h-full transition-all duration-150 ${
                      hud.health > 35 ? 'bg-cyan-400' : 'bg-rose-500'
                    }`}
                    style={{
                      width: `${Math.max(0, (hud.health / hud.maxHealth) * 100)}%`,
                    }}
                  />
                </div>
              </div>

              {/* Counters */}
              <div className="mt-2 grid grid-cols-3 gap-2 text-[10px]">
                <div className="bg-slate-900/90 px-2 py-1 border border-slate-800">
                  <div className="text-slate-400">SHARDS</div>
                  <div className="font-bold text-cyan-300">
                    {hud.shards}/{hud.totalShards}
                  </div>
                </div>
                <div className="bg-slate-900/90 px-2 py-1 border border-slate-800">
                  <div className="text-slate-400">HUSKS</div>
                  <div className="font-bold text-rose-300">
                    {hud.kills} ({hud.aliveHusks})
                  </div>
                </div>
                <div className="bg-slate-900/90 px-2 py-1 border border-slate-800">
                  <div className="text-slate-400">TRUST</div>
                  <div className="font-bold text-emerald-300">{hud.trust}</div>
                </div>
              </div>
            </div>

            {/* Top-Right Controls */}
            <div className="pointer-events-auto flex items-center gap-2">
              {!hud.pointerLocked && (
                <button
                  onClick={() => engine?.requestPointerLock()}
                  className="hidden md:flex items-center gap-1.5 border border-cyan-500/60 bg-[#080c1c]/88 px-3 py-2 text-[11px] font-bold text-cyan-200 hover:border-cyan-300 cursor-pointer"
                  title="Lock mouse for free camera look (ESC to release)"
                >
                  <Crosshair size={14} />
                  MOUSE LOOK
                </button>
              )}
              <button
                onClick={() => engine?.toggleDimensionManual()}
                className="flex items-center gap-1.5 border border-purple-500/60 bg-[#080c1c]/88 px-3 py-2 text-[11px] font-bold text-purple-200 hover:border-purple-300 cursor-pointer"
                title="Shift Dimension (Neon Noir / Inkverse)"
              >
                <Sparkles size={14} />
                SHIFT
              </button>
              <button
                onClick={() => engine?.toggleMute()}
                className="border border-slate-600/80 bg-[#080c1c]/88 p-2 text-slate-200 hover:border-cyan-400 cursor-pointer"
                title="Toggle Audio"
              >
                {hud.muted ? <VolumeX size={15} /> : <Volume2 size={15} />}
              </button>
              <button
                onClick={openReport}
                className="border border-slate-600/80 bg-[#080c1c]/88 px-2.5 py-2 text-xs font-bold text-slate-200 hover:border-cyan-400 cursor-pointer"
                title="Diagnostic Report"
              >
                ?
              </button>
              <button
                onClick={() => engine?.pauseGame()}
                className="flex items-center gap-1 border border-slate-600/80 bg-[#080c1c]/88 px-3 py-2 text-xs font-bold text-slate-200 hover:border-cyan-400 cursor-pointer"
                title="Pause [ESC]"
              >
                <Pause size={14} />
              </button>
            </div>
          </div>

          {/* Center-Top Notifications (Non-occluding) */}
          <div className="flex flex-col items-center gap-2 px-4">
            {hud.dimensionBanner && (
              <div className="border border-fuchsia-400 bg-[#1a0924]/92 px-4 py-1.5 text-xs font-black tracking-[0.22em] text-fuchsia-200">
                {hud.dimensionBanner}
              </div>
            )}
            {hud.nearSpireSeal && (
              <button
                onClick={() => engine?.setVirtualAction('talk', true)}
                className="pointer-events-auto border border-amber-400 bg-amber-950/90 px-4 py-2 text-xs font-bold tracking-wider text-amber-200 hover:bg-amber-900 cursor-pointer"
              >
                [E] SEAL THE SPIRE BLEED
              </button>
            )}
            {hud.dialogue && (
              <div className="max-w-lg border border-cyan-400/70 bg-[#080c1c]/92 px-4 py-2.5 text-center">
                <div className="text-[10px] font-bold tracking-widest text-cyan-300">
                  {hud.dialogue.name}
                </div>
                <div className="mt-0.5 text-xs font-semibold text-white">
                  "{hud.dialogue.line}"
                </div>
              </div>
            )}
          </div>

          {/* Bottom Collapsible Dock: Story Subtitle + Action Pads */}
          <div className="flex flex-col items-center pb-4 px-4 gap-2">
            {hud.subtitle && (
              <div className="max-w-2xl border border-slate-700/80 bg-[#080c1c]/88 px-4 py-2 text-center">
                {hud.actTitle && (
                  <div className="text-[9px] font-bold tracking-[0.25em] text-cyan-300">
                    {hud.actTitle}
                  </div>
                )}
                <div className="text-xs font-semibold tracking-wider text-slate-100">
                  {hud.subtitle}
                </div>
              </div>
            )}

            <button
              onClick={() => setControlsCollapsed((v) => !v)}
              className="pointer-events-auto flex items-center gap-1 border border-slate-700/80 bg-[#080c1c]/88 px-3 py-1 text-[10px] font-bold tracking-wider text-slate-300 hover:text-white cursor-pointer"
            >
              {controlsCollapsed ? (
                <>
                  <ChevronUp size={12} /> SHOW DOCK
                </>
              ) : (
                <>
                  <ChevronDown size={12} /> HIDE DOCK
                </>
              )}
            </button>

            {!controlsCollapsed && (
              <div className="pointer-events-auto flex flex-wrap items-center justify-center gap-2 border border-slate-700/80 bg-[#080c1c]/90 px-4 py-2.5 shadow-xl">
                <div className="flex items-center gap-1 pr-2 border-r border-slate-700 text-[11px] text-slate-300">
                  <span className="font-bold text-white">
                    {Math.round(hud.speed)}
                  </span>
                  <span>m/s</span>
                  {hud.grappling && (
                    <span className="ml-1 text-cyan-300 font-bold">SWING</span>
                  )}
                </div>

                <button
                  onPointerDown={() => engine?.setVirtualAction('web', true)}
                  onPointerUp={() => engine?.setVirtualAction('web', false)}
                  onPointerLeave={() => engine?.setVirtualAction('web', false)}
                  className={`border px-3 py-1.5 text-[11px] font-bold tracking-wider cursor-pointer ${
                    hud.grappling
                      ? 'border-cyan-300 bg-cyan-500 text-slate-950'
                      : 'border-cyan-500/60 bg-cyan-950/60 text-cyan-200 hover:bg-cyan-900/70'
                  }`}
                >
                  WEB [SHIFT/Q]
                </button>
                <button
                  onPointerDown={() => engine?.setVirtualAction('jump', true)}
                  onPointerUp={() => engine?.setVirtualAction('jump', false)}
                  onPointerLeave={() => engine?.setVirtualAction('jump', false)}
                  className="border border-slate-600 bg-slate-900/90 px-3 py-1.5 text-[11px] font-bold tracking-wider text-slate-200 hover:border-slate-400 cursor-pointer"
                >
                  JUMP [SPACE]
                </button>
                <button
                  onClick={() => engine?.setVirtualAction('attack', true)}
                  className="border border-rose-500/70 bg-rose-950/70 px-3 py-1.5 text-[11px] font-bold tracking-wider text-rose-200 hover:bg-rose-900/80 cursor-pointer"
                >
                  ATK [F]
                </button>
                <button
                  onClick={() => engine?.setVirtualAction('talk', true)}
                  className="border border-emerald-500/70 bg-emerald-950/70 px-3 py-1.5 text-[11px] font-bold tracking-wider text-emerald-200 hover:bg-emerald-900/80 cursor-pointer"
                >
                  {hud.nearTalkName ? `TALK: ${hud.nearTalkName} [E]` : 'TALK [E]'}
                </button>
              </div>
            )}
          </div>
        </>
      )}

      {/* === PAUSE MENU === */}
      {hud.mode === 'paused' && (
        <div className="pointer-events-auto fixed inset-0 flex items-center justify-center bg-black/75 p-4">
          <div className="w-full max-w-sm border border-cyan-400/60 bg-[#080c1c] p-6 shadow-2xl">
            <div className="text-[10px] tracking-[0.28em] text-cyan-300">
              NEXUS-9 PAUSED
            </div>
            <h2 className="mt-1 text-2xl font-black text-white">
              WEBVERSE: MULTIVERSE
            </h2>
            <div className="mt-4 flex flex-col gap-2.5">
              <button
                onClick={() => engine?.resumeGame()}
                className="flex items-center justify-center gap-2 border border-cyan-400 bg-cyan-500 py-2.5 text-xs font-bold tracking-wider text-slate-950 hover:bg-cyan-400 cursor-pointer"
              >
                <Play size={14} /> RESUME
              </button>
              <button
                onClick={() => engine?.saveAndQuit()}
                className="flex items-center justify-center gap-2 border border-emerald-500/70 bg-emerald-950/70 py-2.5 text-xs font-bold tracking-wider text-emerald-200 hover:bg-emerald-900 cursor-pointer"
              >
                <Save size={14} /> SAVE + QUIT
              </button>
              <button
                onClick={() => engine?.restartRun()}
                className="flex items-center justify-center gap-2 border border-slate-600 bg-slate-900 py-2.5 text-xs font-bold tracking-wider text-slate-200 hover:border-slate-400 cursor-pointer"
              >
                <RotateCcw size={14} /> RESTART RUN
              </button>
              <button
                onClick={openReport}
                className="flex items-center justify-center gap-2 border border-slate-600 bg-slate-900 py-2.5 text-xs font-bold tracking-wider text-slate-200 hover:border-cyan-400 cursor-pointer"
              >
                <HelpCircle size={14} /> DIAGNOSTIC REPORT (?)
              </button>
              <button
                onClick={() => engine?.quitToTitle()}
                className="flex items-center justify-center gap-2 border border-rose-500/60 bg-rose-950/60 py-2.5 text-xs font-bold tracking-wider text-rose-200 hover:bg-rose-900 cursor-pointer"
              >
                <LogOut size={14} /> QUIT TO TITLE
              </button>
            </div>
          </div>
        </div>
      )}

      {/* === GAME OVER === */}
      {hud.mode === 'gameover' && (
        <div className="pointer-events-auto fixed inset-0 flex items-center justify-center bg-rose-950/80 p-4">
          <div className="w-full max-w-sm border border-rose-400 bg-[#14070c] p-6 text-center shadow-2xl">
            <Shield size={32} className="mx-auto text-rose-400" />
            <h2 className="mt-2 text-2xl font-black text-white">
              KAGE KNOCKED DOWN
            </h2>
            <p className="mt-1 text-xs text-rose-200">
              Shards: {hud.shards}/{hud.totalShards} · Husks Defeated: {hud.kills}
            </p>
            <div className="mt-5 flex flex-col gap-2.5">
              <button
                onClick={() => engine?.restartRun()}
                className="border border-rose-400 bg-rose-500 py-2.5 text-xs font-bold tracking-wider text-white hover:bg-rose-400 cursor-pointer"
              >
                RESPAWN IN NEXUS-9
              </button>
              <button
                onClick={() => engine?.quitToTitle()}
                className="border border-slate-600 bg-slate-900 py-2.5 text-xs font-bold tracking-wider text-slate-200 hover:border-slate-400 cursor-pointer"
              >
                QUIT TO TITLE
              </button>
            </div>
          </div>
        </div>
      )}

      {/* === DIAGNOSTIC REPORT MODAL (?) === */}
      {reportText !== null && (
        <div className="pointer-events-auto fixed inset-0 z-30 flex items-center justify-center bg-black/80 p-4">
          <div className="w-full max-w-lg border border-cyan-400/70 bg-[#080c1c] p-5 shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-700 pb-2.5">
              <span className="text-xs font-bold tracking-widest text-cyan-300">
                WEBVERSE DIAGNOSTIC REPORT
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={copyReport}
                  className="flex items-center gap-1 border border-cyan-500/70 bg-cyan-950/70 px-2.5 py-1 text-[11px] font-bold text-cyan-200 hover:bg-cyan-900 cursor-pointer"
                >
                  {copied ? <Check size={12} /> : <Copy size={12} />}
                  {copied ? 'COPIED' : 'COPY'}
                </button>
                <button
                  onClick={() => setReportText(null)}
                  className="border border-slate-600 bg-slate-900 px-2.5 py-1 text-[11px] font-bold text-slate-200 hover:border-slate-400 cursor-pointer"
                >
                  CLOSE
                </button>
              </div>
            </div>
            <pre className="mt-3 max-h-80 overflow-auto bg-slate-950 p-3 text-[11px] leading-relaxed text-emerald-300 border border-slate-800 select-text">
              {reportText}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
};
