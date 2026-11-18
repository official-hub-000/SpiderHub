// Deterministic PCG32 PRNG matching app/src/main/cpp/core/Pcg32.h
export class Pcg32 {
  private state: bigint;
  private inc: bigint;

  constructor(seed = 12345, seq = 1) {
    this.state = 0n;
    this.inc = (BigInt(seq) << 1n) | 1n;
    this.nextUInt32();
    this.state = (this.state + BigInt(seed)) & 0xffffffffffffffffn;
    this.nextUInt32();
  }

  nextUInt32(): number {
    const oldstate = this.state;
    this.state =
      (oldstate * 6364136223846793005n + this.inc) & 0xffffffffffffffffn;
    const xorshifted = Number(((oldstate >> 18n) ^ oldstate) >> 27n) >>> 0;
    const rot = Number(oldstate >> 59n) & 31;
    return ((xorshifted >>> rot) | (xorshifted << ((-rot) & 31))) >>> 0;
  }

  nextBounded(bound: number): number {
    if (bound <= 0) return 0;
    return this.nextUInt32() % bound;
  }

  nextFloat(): number {
    return (this.nextUInt32() >>> 8) / 16777216.0;
  }
}
