import lottie from 'lottie-web';
import type { AnimationItem } from 'lottie-web';

/**
 * Brand morph: monitor <-> DeX wordmark Lottie sequence, ping-pong pair.
 *
 * - The hero shows two [data-logo-morph] stages playing the same composition
 *   in opposite directions: the left stage starts forward (monitor -> DeX),
 *   a stage marked data-logo-morph="reverse" starts backwards (settled DeX ->
 *   monitor). Other pages keep a single forward stage.
 * - When a stage reaches its end it halts on the settled frame; once every
 *   stage has settled and REPLAY_IDLE_MS has passed, all stages flip direction
 *   IN PLACE (ping-pong) instead of restarting from frame 0, so the pair
 *   stays mirrored forever.
 * - Remnant groups (monitor parts baked into the composition's final frames,
 *   keyframed beyond op) are hidden only while a stage rests on the DeX end;
 *   they are restored as soon as playback leaves that end because they are
 *   legitimate content everywhere else, including the monitor rest state.
 * - A single [data-logo-accent] stage (JSON chosen via data-logo-src) sits
 *   between the morphs and runs its own alternating rounds while the morphs
 *   play: it faces left for one slide, then right for the next, flipping its
 *   CSS-facing class instantly between rounds with a short springy X-nudge
 *   toward the new direction. When the morphs halt, it rests on its fully
 *   drawn neutral frame until playback resumes.
 * - Direction flips fire only while a morph stage is on screen and the tab is
 *   visible; otherwise the cycle defers so nothing animates offscreen.
 * - Clicking any [data-logo-trigger] (nav brand mark) scrolls home and flips
 *   the cycle immediately.
 * - prefers-reduced-motion skips playback entirely; the static fallback logos
 *   stay visible, accent stages are removed from the row, and triggers keep
 *   their normal navigation behavior.
 */

/** Idle hold on a settled pose before the pair flips direction. */
const REPLAY_IDLE_MS = 6000;

/** Safety-net settle delay; the morph runs 7.6s at 60fps. */
const SETTLE_FALLBACK_MS = 8500;

/**
 * Frame where the arrow accent is fully drawn (trim ends reach 100% by frame
 * 120) and still fully opaque (the fade-out starts at frame 150); used as the
 * neutral pose when the accents rest.
 */
const ARROW_REST_FRAME = 120;

/** Monitor remnants identified at the settled frame, by group transform. */
const MONITOR_GROUP_TRANSFORMS = new Set(['matrix(1,0,0,1,124,88)', 'matrix(1,0,0,1,124,168)', 'matrix(1,0,0,1,124,191)']);

/** The shelf-line group uses an identity transform; match it by its path. */
const SHELF_LINE_D_FRAGMENT = '228,124';

interface MorphPlayer {
  stage: HTMLElement;
  anim: AnimationItem;
  /** Stage's own starting direction; multiplied by the shared cycle sign. */
  baseDir: 1 | -1;
  hiddenGroups: SVGGraphicsElement[];
  parked: boolean;
  fallbackTimer: number;
}

export function initLogoMorph(): void {
  const stages = document.querySelectorAll<HTMLElement>('[data-logo-morph]');
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const players: MorphPlayer[] = [];
  const accents: Array<{ anim: AnimationItem; stage: HTMLElement; facing: 'left' | 'right' }> = [];
  let accentsActive = false;

  const NUDGE_CLASSES = ['accent-nudge-left', 'accent-nudge-right'];

  /** Shared cycle sign: 1 = stages run their base direction, -1 = flipped. */
  let cycleDir: 1 | -1 = 1;
  let flipTimer = 0;
  let visibleStages = 0;

  /** Play one arrow slide toward the entry's current facing, then chain. */
  const playAccentRound = (entry: { anim: AnimationItem; stage: HTMLElement; facing: 'left' | 'right' }) => {
    entry.stage.classList.remove(...NUDGE_CLASSES);
    void entry.stage.offsetWidth;
    entry.stage.classList.add(entry.facing === 'left' ? 'accent-nudge-left' : 'accent-nudge-right');
    entry.anim.goToAndPlay(0, true);
  };

  const startAccents = () => {
    if (accents.length === 0) return;
    accentsActive = true;
    accents.forEach((entry) => {
      entry.anim.setSpeed(1);
      playAccentRound(entry);
    });
  };

  const stopAccents = () => {
    accentsActive = false;
    accents.forEach(({ anim }) => anim.goToAndStop(ARROW_REST_FRAME, true));
  };

  const showRemnants = (player: MorphPlayer) => {
    player.hiddenGroups.forEach((el) => {
      el.style.display = '';
    });
    player.hiddenGroups.length = 0;
  };

  const hideRemnants = (player: MorphPlayer) => {
    const scaleGroup = player.stage.querySelector('svg g[clip-path] > g');
    if (!scaleGroup) return;
    Array.from(scaleGroup.children).forEach((child) => {
      const el = child as SVGGraphicsElement;
      if (player.hiddenGroups.includes(el)) return;
      const transform = el.getAttribute('transform') || '';
      let hide = MONITOR_GROUP_TRANSFORMS.has(transform);
      if (transform === 'matrix(1,0,0,1,0,0)') {
        const d = el.querySelector('path')?.getAttribute('d') || '';
        hide = d.includes(SHELF_LINE_D_FRAGMENT);
      }
      if (hide) {
        el.style.display = 'none';
        player.hiddenGroups.push(el);
      }
    });
  };

  const settlePlayer = (player: MorphPlayer) => {
    if (player.parked) return;
    player.parked = true;
    window.clearTimeout(player.fallbackTimer);
    if (player.anim.currentRawFrame > player.anim.totalFrames / 2) {
      hideRemnants(player);
    } else {
      showRemnants(player);
    }
    if (players.every((p) => p.parked)) {
      stopAccents();
      scheduleFlip();
    }
  };

  const scheduleFlip = () => {
    window.clearTimeout(flipTimer);
    flipTimer = window.setTimeout(flipCycle, REPLAY_IDLE_MS);
  };

  const flipCycle = () => {
    if (players.length === 0) return;
    if (document.hidden || visibleStages === 0) {
      scheduleFlip();
      return;
    }
    window.clearTimeout(flipTimer);
    cycleDir = cycleDir === 1 ? -1 : 1;
    players.forEach((player) => {
      player.parked = false;
      showRemnants(player);
      player.anim.setSpeed(1);
      player.anim.setDirection((player.baseDir * cycleDir) as 1 | -1);
      player.anim.play();
      player.fallbackTimer = window.setTimeout(() => settlePlayer(player), SETTLE_FALLBACK_MS);
    });
    startAccents();
  };

  stages.forEach((stage) => {
    const baseDir: 1 | -1 = stage.dataset.logoMorph === 'reverse' ? -1 : 1;

    if (reduced) {
      stage.classList.add('morph-static');
      return;
    }

    const player: MorphPlayer = {
      stage,
      anim: null as unknown as AnimationItem,
      baseDir,
      hiddenGroups: [],
      parked: false,
      fallbackTimer: 0,
    };

    player.anim = lottie.loadAnimation({
      container: stage,
      renderer: 'svg',
      loop: false,
      autoplay: baseDir === 1,
      path: '/assets/brand/dex-morph.json',
    });
    player.anim.addEventListener('DOMLoaded', () => {
      stage.classList.add('morph-loaded');
      if (baseDir === -1) {
        player.anim.setDirection(-1);
        player.anim.goToAndPlay(Math.max(0, player.anim.totalFrames - 1), true);
      }
      startAccents();
      // Safety net: 'complete' can be swallowed under throttled time.
      player.fallbackTimer = window.setTimeout(() => settlePlayer(player), SETTLE_FALLBACK_MS);
    });
    player.anim.addEventListener('complete', () => settlePlayer(player));
    players.push(player);
  });

  document.querySelectorAll<HTMLElement>('[data-logo-accent]').forEach((stage) => {
    if (reduced) {
      stage.classList.add('morph-static');
      return;
    }
    const src = stage.dataset.logoSrc;
    if (!src) return;
    const accent = lottie.loadAnimation({
      container: stage,
      renderer: 'svg',
      loop: false,
      autoplay: false,
      path: src,
    });
    const entry: { anim: AnimationItem; stage: HTMLElement; facing: 'left' | 'right' } = {
      anim: accent,
      stage,
      facing: stage.classList.contains('accent-alt') ? 'left' : 'right',
    };
    accent.addEventListener('DOMLoaded', () => stage.classList.add('morph-loaded'));
    accent.addEventListener('complete', () => {
      if (!accentsActive) return;
      entry.facing = entry.facing === 'left' ? 'right' : 'left';
      stage.classList.toggle('accent-alt');
      playAccentRound(entry);
    });
    accents.push(entry);
  });

  const visibilityObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        visibleStages += entry.isIntersecting ? 1 : -1;
      });
      visibleStages = Math.max(0, visibleStages);
    },
    { threshold: 0.25 },
  );
  stages.forEach((stage) => visibilityObserver.observe(stage));

  document.querySelectorAll<HTMLElement>('[data-logo-trigger]').forEach((trigger) => {
    trigger.addEventListener('click', (event) => {
      if (players.length === 0) return;
      event.preventDefault();
      window.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' });
      window.setTimeout(flipCycle, reduced ? 0 : 350);
    });
  });
}
