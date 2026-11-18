import type { Vec3 } from './types';

export interface StoryBeat {
  line: string;
  duration: number;
}

export interface StoryAct {
  title: string;
  beats: StoryBeat[];
}

export class StorySystem {
  private acts: StoryAct[] = [
    {
      title: 'ACT I  THE UNDERFLOW',
      beats: [
        { line: 'NEXUS-9  2147. THE MULTIVERSE IS BLEEDING.', duration: 5.0 },
        { line: 'KAGE: THEY CALL ME A GLITCH. THEY ARE RIGHT.', duration: 4.5 },
        { line: 'A PORTAL OPENS. A CITY THAT IS NOT OURS LOOKS BACK.', duration: 4.5 },
        { line: 'I CAN FEEL THE OTHER GRAVITY IN MY BONES.', duration: 4.0 },
      ],
    },
    {
      title: 'ACT II  THE LANTERNKEEPERS',
      beats: [
        { line: 'THE LANTERNKEEPERS SOLD THE CITY TO FOUR DIMENSIONS.', duration: 4.5 },
        { line: 'EACH PORTAL REWRITES THE RULES: INK, NEON, GLITCH, PAPER.', duration: 5.0 },
        { line: 'IF I CAN REACH THE SPIRE, I CAN SEAL THE BLEED.', duration: 4.5 },
        { line: 'FIRST I NEED TO BEAT THE HUSKS INTO THE STREET.', duration: 4.0 },
      ],
    },
    {
      title: 'ACT III  THE SPIRE',
      beats: [
        { line: 'THE SPIRE IS NOT A BUILDING. IT IS A DOOR.', duration: 5.0 },
        { line: 'KAGE: I WAS MADE FROM THE BLEED. LET ME UNMAKE IT.', duration: 5.0 },
        { line: 'SWING. FIGHT. AND WHEN THE STYLE CHANGES, CHANGE WITH IT.', duration: 5.0 },
        { line: 'WEBVERSE  THE END OF THE BEGINNING.', duration: 4.0 },
      ],
    },
  ];

  private act = 0;
  private beat = 0;
  private t = 0;

  reset(): void {
    this.act = 0;
    this.beat = 0;
    this.t = 0;
  }

  active(): boolean {
    return this.act < this.acts.length;
  }

  update(dt: number): void {
    if (!this.active()) return;
    this.t += dt;
    const curBeat = this.acts[this.act].beats[this.beat];
    if (this.t >= curBeat.duration) {
      this.t = 0;
      this.beat++;
      if (this.beat >= this.acts[this.act].beats.length) {
        this.beat = 0;
        this.act++;
      }
    }
  }

  subtitle(): string {
    if (!this.active()) return '';
    return this.acts[this.act].beats[this.beat].line;
  }

  actTitle(): string {
    if (!this.active()) return '';
    return this.acts[this.act].title;
  }

  skip(): void {
    this.act = this.acts.length;
  }
}

export interface CameraShot {
  camPos: Vec3;
  camEnd: Vec3;
  lookAt: Vec3;
  lookEnd: Vec3;
  duration: number;
  subtitle: string;
}

function ease(t: number): number {
  return t * t * (3.0 - 2.0 * t);
}

export class ShotRail {
  private shots: CameraShot[] = [];
  private index = -1;
  private t = 0;
  public take = 1;

  startOpeningCutscene(): void {
    this.shots = [
      {
        camPos: { x: 0, y: 88, z: 128 },
        camEnd: { x: 60, y: 62, z: 96 },
        lookAt: { x: 0, y: 30, z: 0 },
        lookEnd: { x: 0, y: 34, z: 0 },
        duration: 4.5,
        subtitle: 'NEXUS-9  2147',
      },
      {
        camPos: { x: 60, y: 46, z: 70 },
        camEnd: { x: 26, y: 22, z: 44 },
        lookAt: { x: 0, y: 40, z: 0 },
        lookEnd: { x: 0, y: 26, z: 0 },
        duration: 4.5,
        subtitle: 'THE MULTIVERSE IS BLEEDING INTO OUR SKY',
      },
      {
        camPos: { x: -34, y: 8, z: 30 },
        camEnd: { x: -12, y: 6, z: 22 },
        lookAt: { x: 0, y: 18, z: 0 },
        lookEnd: { x: 0, y: 12, z: 0 },
        duration: 4.5,
        subtitle: 'EACH PORTAL REWRITES THE RULES OF THE STREET',
      },
      {
        camPos: { x: 28, y: 15, z: 38 },
        camEnd: { x: 10, y: 11, z: 22 },
        lookAt: { x: 0, y: 14, z: 0 },
        lookEnd: { x: 0, y: 10, z: 0 },
        duration: 4.0,
        subtitle: 'SWING. FIGHT. SURVIVE.',
      },
    ];
    this.index = 0;
    this.t = 0;
  }

  startEndingCutscene(): void {
    this.shots = [
      {
        camPos: { x: -30, y: 10, z: 36 },
        camEnd: { x: -16, y: 22, z: 26 },
        lookAt: { x: 0, y: 4, z: 0 },
        lookEnd: { x: 0, y: 60, z: 0 },
        duration: 5.0,
        subtitle: 'THE SPIRE OPENS',
      },
      {
        camPos: { x: -14, y: 22, z: 20 },
        camEnd: { x: -6, y: 46, z: 10 },
        lookAt: { x: 0, y: 60, z: 0 },
        lookEnd: { x: 0, y: 92, z: 0 },
        duration: 5.0,
        subtitle: 'KAGE: I WAS MADE FROM THE BLEED. LET ME UNMAKE IT.',
      },
      {
        camPos: { x: 26, y: 70, z: -26 },
        camEnd: { x: 8, y: 88, z: -10 },
        lookAt: { x: 0, y: 86, z: 0 },
        lookEnd: { x: 0, y: 60, z: 0 },
        duration: 6.0,
        subtitle: 'WEBVERSE  THE BLEED IS SEALED',
      },
    ];
    this.index = 0;
    this.t = 0;
  }

  startMovie(take: number): void {
    const kBeat = 4.6;
    this.take = take;
    if (take === 2) {
      this.shots = [
        {
          camPos: { x: 0, y: 120, z: 190 },
          camEnd: { x: 90, y: 88, z: 150 },
          lookAt: { x: 0, y: 34, z: 0 },
          lookEnd: { x: 0, y: 30, z: 0 },
          duration: kBeat,
          subtitle: 'INKVERSE. THE CITY DRAWN BY A HAND THAT HATES US.',
        },
        {
          camPos: { x: 90, y: 70, z: 118 },
          camEnd: { x: 58, y: 44, z: 80 },
          lookAt: { x: 0, y: 40, z: 0 },
          lookEnd: { x: 0, y: 34, z: 0 },
          duration: kBeat,
          subtitle: 'EVERY INK LINE HERE IS A WOUND THAT DID NOT HEAL.',
        },
        {
          camPos: { x: 58, y: 34, z: 66 },
          camEnd: { x: 30, y: 22, z: 42 },
          lookAt: { x: 12, y: 26, z: 8 },
          lookEnd: { x: 6, y: 20, z: 4 },
          duration: kBeat,
          subtitle: 'KAGE: IN THIS WORLD I AM THE SKETCH, NOT THE INK.',
        },
        {
          camPos: { x: 30, y: 16, z: 34 },
          camEnd: { x: 14, y: 12, z: 22 },
          lookAt: { x: 0, y: 22, z: 0 },
          lookEnd: { x: 0, y: 16, z: 0 },
          duration: kBeat,
          subtitle: 'THE HALFTONE IS A WALL. I WILL DOT IT TO DUST.',
        },
        {
          camPos: { x: -46, y: 26, z: 74 },
          camEnd: { x: -26, y: 18, z: 48 },
          lookAt: { x: 0, y: 30, z: 0 },
          lookEnd: { x: 0, y: 26, z: 0 },
          duration: kBeat,
          subtitle: 'THE LANTERNKEEPERS DRAW THE CITY TO KEEP IT STILL.',
        },
        {
          camPos: { x: -26, y: 14, z: 40 },
          camEnd: { x: -10, y: 9, z: 24 },
          lookAt: { x: 0, y: 24, z: 0 },
          lookEnd: { x: 0, y: 20, z: 0 },
          duration: kBeat,
          subtitle: 'A PAGE TEARS. THE BLEED SHOWS THROUGH THE GAP.',
        },
        {
          camPos: { x: -16, y: 14, z: 34 },
          camEnd: { x: 0, y: 11, z: 24 },
          lookAt: { x: 0, y: 18, z: 0 },
          lookEnd: { x: 0, y: 14, z: 0 },
          duration: kBeat,
          subtitle: 'KAGE: FOUR DIMENSIONS, ONE WEB. WATCH ME REDRAW IT.',
        },
        {
          camPos: { x: 2, y: 4.5, z: 11 },
          camEnd: { x: 6, y: 3.6, z: 7 },
          lookAt: { x: 0, y: 12, z: 0 },
          lookEnd: { x: 0, y: 9, z: 0 },
          duration: kBeat,
          subtitle: 'WEBVERSE: INKVERSE. THE SECOND CUT.',
        },
      ];
    } else {
      this.shots = [
        {
          camPos: { x: 0, y: 120, z: 190 },
          camEnd: { x: 90, y: 88, z: 150 },
          lookAt: { x: 0, y: 34, z: 0 },
          lookEnd: { x: 0, y: 30, z: 0 },
          duration: kBeat,
          subtitle: 'NEXUS-9  2147. THE MULTIVERSE IS BLEEDING.',
        },
        {
          camPos: { x: 90, y: 70, z: 118 },
          camEnd: { x: 58, y: 44, z: 80 },
          lookAt: { x: 0, y: 40, z: 0 },
          lookEnd: { x: 0, y: 34, z: 0 },
          duration: kBeat,
          subtitle: 'KAGE: THEY CALL ME A GLITCH. THEY ARE RIGHT.',
        },
        {
          camPos: { x: 58, y: 34, z: 66 },
          camEnd: { x: 30, y: 22, z: 42 },
          lookAt: { x: 12, y: 26, z: 8 },
          lookEnd: { x: 6, y: 20, z: 4 },
          duration: kBeat,
          subtitle: 'A PORTAL OPENS. A CITY THAT IS NOT OURS LOOKS BACK.',
        },
        {
          camPos: { x: 30, y: 16, z: 34 },
          camEnd: { x: 14, y: 12, z: 22 },
          lookAt: { x: 0, y: 22, z: 0 },
          lookEnd: { x: 0, y: 16, z: 0 },
          duration: kBeat,
          subtitle: 'I CAN FEEL THE OTHER GRAVITY IN MY BONES.',
        },
        {
          camPos: { x: -46, y: 26, z: 74 },
          camEnd: { x: -26, y: 18, z: 48 },
          lookAt: { x: 0, y: 30, z: 0 },
          lookEnd: { x: 0, y: 26, z: 0 },
          duration: kBeat,
          subtitle: 'THE LANTERNKEEPERS SOLD THE CITY TO FOUR DIMENSIONS.',
        },
        {
          camPos: { x: -26, y: 14, z: 40 },
          camEnd: { x: -10, y: 9, z: 24 },
          lookAt: { x: 0, y: 24, z: 0 },
          lookEnd: { x: 0, y: 20, z: 0 },
          duration: kBeat,
          subtitle: 'EACH PORTAL REWRITES THE RULES: INK, NEON, GLITCH, PAPER.',
        },
        {
          camPos: { x: -16, y: 14, z: 34 },
          camEnd: { x: 0, y: 11, z: 24 },
          lookAt: { x: 0, y: 18, z: 0 },
          lookEnd: { x: 0, y: 14, z: 0 },
          duration: kBeat,
          subtitle: 'IF I CAN REACH THE SPIRE, I CAN SEAL THE BLEED.',
        },
        {
          camPos: { x: 2, y: 4.5, z: 11 },
          camEnd: { x: 6, y: 3.6, z: 7 },
          lookAt: { x: 0, y: 12, z: 0 },
          lookEnd: { x: 0, y: 9, z: 0 },
          duration: kBeat,
          subtitle: 'FIRST I NEED TO BEAT THE HUSKS INTO THE STREET.',
        },
        {
          camPos: { x: 70, y: 30, z: -80 },
          camEnd: { x: 40, y: 22, z: -48 },
          lookAt: { x: 0, y: 60, z: 0 },
          lookEnd: { x: 0, y: 52, z: 0 },
          duration: kBeat,
          subtitle: 'THE SPIRE IS NOT A BUILDING. IT IS A DOOR.',
        },
        {
          camPos: { x: 40, y: 18, z: -48 },
          camEnd: { x: 22, y: 12, z: -28 },
          lookAt: { x: 0, y: 84, z: 0 },
          lookEnd: { x: 0, y: 74, z: 0 },
          duration: kBeat,
          subtitle: 'KAGE: I WAS MADE FROM THE BLEED. LET ME UNMAKE IT.',
        },
        {
          camPos: { x: 22, y: 9, z: -28 },
          camEnd: { x: 10, y: 6, z: -16 },
          lookAt: { x: 0, y: 92, z: 0 },
          lookEnd: { x: 0, y: 82, z: 0 },
          duration: kBeat,
          subtitle: 'SWING. FIGHT. AND WHEN THE STYLE CHANGES, CHANGE WITH IT.',
        },
        {
          camPos: { x: 10, y: 5, z: -16 },
          camEnd: { x: 0, y: 4, z: -8 },
          lookAt: { x: 0, y: 96, z: 0 },
          lookEnd: { x: 0, y: 86, z: 0 },
          duration: kBeat,
          subtitle: 'WEBVERSE  THE END OF THE BEGINNING.',
        },
      ];
    }
    this.index = 0;
    this.t = 0;
  }

  active(): boolean {
    return this.index >= 0 && this.index < this.shots.length;
  }

  skip(): void {
    this.index = -1;
  }

  subtitle(): string {
    if (!this.active()) return '';
    return this.shots[this.index].subtitle;
  }

  update(dt: number): { pos: Vec3; look: Vec3 } | null {
    if (!this.active()) return null;
    this.t += dt;
    if (this.t >= this.shots[this.index].duration) {
      this.t = 0;
      this.index++;
      if (this.index >= this.shots.length) {
        this.index = -1;
        return null;
      }
    }
    const cur = this.shots[this.index];
    const u = ease(Math.max(0, Math.min(1, this.t / cur.duration)));
    return {
      pos: {
        x: cur.camPos.x + (cur.camEnd.x - cur.camPos.x) * u,
        y: cur.camPos.y + (cur.camEnd.y - cur.camPos.y) * u,
        z: cur.camPos.z + (cur.camEnd.z - cur.camPos.z) * u,
      },
      look: {
        x: cur.lookAt.x + (cur.lookEnd.x - cur.lookAt.x) * u,
        y: cur.lookAt.y + (cur.lookEnd.y - cur.lookAt.y) * u,
        z: cur.lookAt.z + (cur.lookEnd.z - cur.lookAt.z) * u,
      },
    };
  }
}
