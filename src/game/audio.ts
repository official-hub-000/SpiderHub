// Procedural Web Audio synthesizer replacing Oboe Audio.cpp — zero audio files
export class AudioEngine {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private droneGain: GainNode | null = null;
  private padGain: GainNode | null = null;
  private padOscs: OscillatorNode[] = [];
  private bassOsc: OscillatorNode | null = null;
  private bassGain: GainNode | null = null;
  private chordIndex = 0;
  private chordTimer = 0;
  private pulseTimer = 0;
  private intensity = 0.25;
  public muted = false;

  private readonly chords = [
    [110.0, 130.81, 164.81, 196.0], // Am7
    [87.31, 130.81, 174.61, 220.0], // Fmaj7
    [98.0, 146.83, 196.0, 246.94], // G6
    [82.41, 123.47, 164.81, 196.0], // Em7
  ];

  unlock(): void {
    if (this.ctx) {
      if (this.ctx.state === 'suspended') {
        void this.ctx.resume();
      }
      return;
    }
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext })
        .webkitAudioContext;
    if (!AudioCtx) return;

    this.ctx = new AudioCtx();
    this.master = this.ctx.createGain();
    this.master.gain.value = this.muted ? 0 : 0.35;
    this.master.connect(this.ctx.destination);

    // City low drone
    const drone = this.ctx.createOscillator();
    drone.type = 'sawtooth';
    drone.frequency.value = 55.0;
    const droneFilter = this.ctx.createBiquadFilter();
    droneFilter.type = 'lowpass';
    droneFilter.frequency.value = 140;
    this.droneGain = this.ctx.createGain();
    this.droneGain.gain.value = 0.14;
    drone.connect(droneFilter);
    droneFilter.connect(this.droneGain);
    this.droneGain.connect(this.master);
    drone.start();

    // 4-chord procedural pad
    const padFilter = this.ctx.createBiquadFilter();
    padFilter.type = 'lowpass';
    padFilter.frequency.value = 620;
    this.padGain = this.ctx.createGain();
    this.padGain.gain.value = 0.08;
    padFilter.connect(this.padGain);
    this.padGain.connect(this.master);

    const baseChord = this.chords[0];
    for (let i = 0; i < 3; i++) {
      const osc = this.ctx.createOscillator();
      osc.type = i === 0 ? 'triangle' : 'sine';
      osc.frequency.value = baseChord[i + 1];
      osc.connect(padFilter);
      osc.start();
      this.padOscs.push(osc);
    }

    // Bass pulse
    this.bassOsc = this.ctx.createOscillator();
    this.bassOsc.type = 'triangle';
    this.bassOsc.frequency.value = baseChord[0];
    this.bassGain = this.ctx.createGain();
    this.bassGain.gain.value = 0.0;
    this.bassOsc.connect(this.bassGain);
    this.bassGain.connect(this.master);
    this.bassOsc.start();
  }

  setMuted(m: boolean): void {
    this.muted = m;
    if (this.master && this.ctx) {
      this.master.gain.setTargetAtTime(m ? 0 : 0.35, this.ctx.currentTime, 0.05);
    }
  }

  setIntensity(v: number): void {
    this.intensity = Math.max(0.1, Math.min(1.0, v));
  }

  update(dt: number, activePlay: boolean): void {
    if (!this.ctx || !this.padGain || !this.droneGain || !this.bassGain) return;
    const now = this.ctx.currentTime;
    const targetPad = activePlay ? 0.06 + this.intensity * 0.08 : 0.03;
    this.padGain.gain.setTargetAtTime(targetPad, now, 0.25);
    this.droneGain.gain.setTargetAtTime(activePlay ? 0.14 : 0.06, now, 0.25);

    this.chordTimer += dt;
    if (this.chordTimer >= 4.0) {
      this.chordTimer = 0;
      this.chordIndex = (this.chordIndex + 1) % this.chords.length;
      const c = this.chords[this.chordIndex];
      for (let i = 0; i < this.padOscs.length; i++) {
        this.padOscs[i].frequency.setTargetAtTime(c[i + 1], now, 0.15);
      }
      if (this.bassOsc) {
        this.bassOsc.frequency.setTargetAtTime(c[0], now, 0.05);
      }
    }

    this.pulseTimer += dt;
    const pulsePeriod = 0.5 - this.intensity * 0.18;
    if (activePlay && this.pulseTimer >= pulsePeriod) {
      this.pulseTimer = 0;
      this.bassGain.gain.cancelScheduledValues(now);
      this.bassGain.gain.setValueAtTime(0.02 + this.intensity * 0.16, now);
      this.bassGain.gain.exponentialRampToValueAtTime(0.001, now + 0.24);
    }
  }

  playTone(freq: number, dur: number, vol: number, type: OscillatorType = 'sine'): void {
    if (!this.ctx || !this.master || this.muted) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, now);
    g.gain.setValueAtTime(vol, now);
    g.gain.exponentialRampToValueAtTime(0.001, now + dur);
    osc.connect(g);
    g.connect(this.master);
    osc.start(now);
    osc.stop(now + dur + 0.02);
  }

  playWebThwip(): void {
    if (!this.ctx || !this.master || this.muted) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(520, now);
    osc.frequency.exponentialRampToValueAtTime(140, now + 0.14);
    g.gain.setValueAtTime(0.22, now);
    g.gain.exponentialRampToValueAtTime(0.001, now + 0.15);
    osc.connect(g);
    g.connect(this.master);
    osc.start(now);
    osc.stop(now + 0.16);
  }

  playJump(): void {
    this.playTone(220, 0.16, 0.12, 'sine');
  }

  playMeleeSwing(): void {
    if (!this.ctx || !this.master || this.muted) return;
    const now = this.ctx.currentTime;
    const bufferSize = Math.floor(this.ctx.sampleRate * 0.12);
    const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      data[i] = (Math.random() * 2 - 1) * (1 - i / bufferSize);
    }
    const noise = this.ctx.createBufferSource();
    noise.buffer = buffer;
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(900, now);
    const g = this.ctx.createGain();
    g.gain.setValueAtTime(0.25, now);
    g.gain.exponentialRampToValueAtTime(0.001, now + 0.12);
    noise.connect(filter);
    filter.connect(g);
    g.connect(this.master);
    noise.start(now);
  }

  playHitCrunch(): void {
    this.playTone(110, 0.12, 0.25, 'sawtooth');
    this.playTone(340, 0.08, 0.16, 'square');
  }

  playShardChime(): void {
    this.playTone(660, 0.18, 0.18, 'triangle');
    setTimeout(() => this.playTone(880, 0.24, 0.16, 'sine'), 60);
  }

  playPortalWarp(): void {
    if (!this.ctx || !this.master || this.muted) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(160, now);
    osc.frequency.exponentialRampToValueAtTime(720, now + 0.35);
    g.gain.setValueAtTime(0.22, now);
    g.gain.exponentialRampToValueAtTime(0.001, now + 0.38);
    osc.connect(g);
    g.connect(this.master);
    osc.start(now);
    osc.stop(now + 0.4);
  }

  playDamage(): void {
    this.playTone(95, 0.22, 0.28, 'sawtooth');
  }

  playSpireSeal(): void {
    [220, 277.18, 329.63, 440, 659.25].forEach((f, idx) => {
      setTimeout(() => this.playTone(f, 0.65, 0.18, 'triangle'), idx * 85);
    });
  }

  dispose(): void {
    if (this.ctx) {
      void this.ctx.close();
      this.ctx = null;
    }
  }
}
