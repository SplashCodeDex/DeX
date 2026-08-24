export const site = {
  name: 'DeX',
  studio: 'Dex Studios',
  description:
    'DeX moves files straight between your Android phone and your Windows or macOS computer over Wi-Fi. No cables, no cloud, no accounts — scan once and send.',
  downloadPage: '/download',
  pricingPage: '/pricing',
  latestVersion: '10.1',
  receivedFolder: '~/Downloads/DeX',
  /**
   * Release targets. Buttons never show the host name in the UI; swap these
   * to direct CDN/store URLs when distribution moves off release staging.
   */
  releaseUrl: 'https://github.com/SplashCodeDex/DeX/releases/latest',
  desktop: [
    { id: 'windows', name: 'Windows', requirement: 'Windows 10+', available: true, icon: '/assets/platform/win.png' },
    { id: 'macos', name: 'macOS', requirement: 'macOS 12+', available: true, icon: '/assets/platform/mac.png' },
    { id: 'linux', name: 'Linux', requirement: 'At launch', available: false, icon: '/assets/platform/linux.png' },
  ],
  mobile: [
    {
      id: 'ios',
      name: 'iPhone & iPad',
      requirement: 'Companion app',
      icon: '/assets/platform/apple.png',
      iconDark: '/assets/platform/apple-white.png',
      badge: '/assets/stores/appstore.svg',
      badgeAlt: 'Download on the App Store',
      available: false,
    },
    {
      id: 'android',
      name: 'Android',
      requirement: 'Companion app',
      icon: '/assets/platform/android.png',
      badge: '/assets/stores/googleplaystore.svg',
      badgeAlt: 'Get it on Google Play',
      available: false,
    },
  ],
  pricing: {
    free: {
      price: '$0',
      period: 'forever',
      features: [
        'Phone-to-computer transfers on the same Wi-Fi',
        'Connect by scanning a code',
        'Copy on your phone, paste on your computer',
        'Browse folders on either device before sending',
        'Works alongside LocalSend apps',
        'Windows, macOS, and Android',
      ],
    },
    pro: {
      name: 'Pro',
      price: '$29',
      period: 'one-time',
      badge: 'Most popular',
      cta: 'Get Pro',
      features: [
        'Everything in Free',
        'Send anywhere, even across the internet',
        'Still works on strict hotel and office networks',
        'Keep every phone and computer connected',
        'Priority support',
        'Lifetime updates',
      ],
    },
  },
} as const;
