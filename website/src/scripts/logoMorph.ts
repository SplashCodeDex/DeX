import type { AnimationItem } from 'lottie-web';

/**
 * Brand morph: monitor -> DeX wordmark Lottie sequence.
 *
 * - Plays once on page load inside [data-logo-morph], parking on the settled
 *   final frame (the reference cut parks at frame 455 after its deceleration).
 * - Clicking any [data-logo-trigger] (nav brand mark) scrolls home and replays.
 * - prefers-reduced-motion skips playback entirely; the static fallback logos
 *   stay visible and triggers keep their normal navigation behavior.
 */
export function initLogoMorph(): void {
  const stage = document.querySelector<HTMLElement>('[data-logo-morph]');
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  let anim: AnimationItem | null = null;

  if (stage && !reduced) {
    import('lottie-web').then(({ default: lottie }) => {
      anim = lottie.loadAnimation({
        container: stage,
        renderer: 'svg',
        loop: false,
        autoplay: true,
        path: '/assets/brand/dex-morph.json',
      });
      anim.addEventListener('DOMLoaded', () => stage.classList.add('morph-loaded'));
    });
  } else if (stage) {
    stage.classList.add('morph-static');
  }

  document.querySelectorAll<HTMLElement>('[data-logo-trigger]').forEach((trigger) => {
    trigger.addEventListener('click', (event) => {
      if (!anim) return;
      event.preventDefault();
      window.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' });
      window.setTimeout(() => {
        anim!.setSpeed(1);
        anim!.goToAndPlay(0, true);
      }, reduced ? 0 : 350);
    });
  });
}
