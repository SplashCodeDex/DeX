export function enhancePage(): void {
  bindThemeToggles();
  initStickyCta();
  initReveals();
  initParallax();
}

function bindThemeToggles(): void {
  document.querySelectorAll<HTMLElement>('[data-theme-toggle]').forEach((button) => {
    if (button.dataset.themeBound === 'true') return;
    button.dataset.themeBound = 'true';
    button.addEventListener('click', () => {
      const next = document.documentElement.classList.contains('dark') ? 'light' : 'dark';
      localStorage.setItem('DeX-theme', next);
      document.documentElement.classList.toggle('dark', next === 'dark');
    });
  });
}

function initStickyCta(): void {
  const stickyCta = document.getElementById('sticky-cta');
  if (!stickyCta) return;
  function updateStickyCta(): void {
    stickyCta!.classList.toggle('is-visible', window.scrollY > 600);
  }
  updateStickyCta();
  window.addEventListener('scroll', updateStickyCta, { passive: true });
}

function initReveals(): void {
  const revealTargets = document.querySelectorAll<HTMLElement>('.landing-reveal');
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    revealTargets.forEach((el) => el.classList.add('is-visible'));
    return;
  }
  const revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          revealObserver.unobserve(entry.target);
        }
      });
    },
    { rootMargin: '0px 0px -100px 0px' }
  );
  revealTargets.forEach((el) => revealObserver.observe(el));
}

function initParallax(): void {
  const parallaxTiles = document.querySelectorAll<HTMLElement>('[data-parallax]');
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || parallaxTiles.length === 0) {
    return;
  }
  const speeds: Record<string, number> = { slow: 25, medium: 50, fast: 100 };
  const state = Array.from(parallaxTiles).map((tile) => ({
    tile,
    section: tile.closest('section'),
    speed: speeds[tile.dataset.parallax ?? 'medium'] ?? 50,
    current: 0,
  }));

  state.forEach(({ tile }) => {
    tile.style.willChange = 'transform';
  });

  const tick = (): void => {
    for (const item of state) {
      if (!item.section) continue;
      const rect = item.section.getBoundingClientRect();
      if (rect.bottom < -200 || rect.top > window.innerHeight + 200) continue;
      const total = rect.height + window.innerHeight;
      const progress = Math.min(1, Math.max(0, (window.innerHeight - rect.top) / total));
      const target = item.speed - progress * item.speed * 2;
      item.current += (target - item.current) * 0.12;
      if (Math.abs(target - item.current) < 0.01) item.current = target;
      item.tile.style.transform = `translateY(${item.current.toFixed(2)}px)`;
    }
    requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}
