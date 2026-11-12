const bottlePalette = {
  100: '#FFFFFF',
  200: '#F3F8F5',
  300: '#D8E6DF',
  500: '#1F6F54',
  700: '#1F6F54',
  800: '#0B3B2E',
  900: '#0B3B2E',
};

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Be Vietnam Pro', 'system-ui', 'sans-serif'] },
      colors: {
        primary: {
          DEFAULT: bottlePalette[800],
          hover: bottlePalette[500],
          fixed: bottlePalette[300],
          soft: bottlePalette[200],
          faint: bottlePalette[200],
        },
        secondary: {
          DEFAULT: bottlePalette[800],
          hover: bottlePalette[500],
          fixed: bottlePalette[300],
          soft: bottlePalette[200],
        },
        accent: bottlePalette[500],
        link: bottlePalette[500],
        highlight: '#E0B25A',
        admin: {
          sidebar: bottlePalette[900],
          group: bottlePalette[500],
          divider: bottlePalette[300],
          label: '#E0B25A',
          text: bottlePalette[200],
        },
        home: {
          header: '#0B3B2E',
          gold: '#E0B25A',
          'gold-hover': '#EBC274',
          ink: '#3A2208',
          bronze: '#A8791C',
        },
        sage: { DEFAULT: bottlePalette[300], light: bottlePalette[200] },
        background: bottlePalette[200],
        surface: {
          DEFAULT: bottlePalette[100],
          low: bottlePalette[200],
          container: bottlePalette[200],
          high: bottlePalette[500],
          hover: '#E6F0EA',
        },
        text: {
          primary: bottlePalette[900],
          secondary: '#374151',
          muted: '#6B7280',
        },
        border: bottlePalette[300],
        success: {
          ...bottlePalette,
          DEFAULT: bottlePalette[500],
          soft: bottlePalette[200],
        },
        warning: { DEFAULT: '#8A5B17', soft: '#F8F1DF' },
        error: { DEFAULT: '#B04444', soft: '#F8EAEA' },
        info: { DEFAULT: '#3F6475', soft: '#EAF0F1' },
      },
      fontSize: { '2xs': ['0.6875rem', { lineHeight: '1rem' }] },
      borderRadius: { sm: '6px', md: '10px', lg: '14px', xl: '18px' },
      boxShadow: {
        sm: '0 1px 2px rgba(11, 59, 46, 0.08)',
        md: '0 10px 30px rgba(11, 59, 46, 0.12)',
        lg: '0 24px 60px rgba(11, 59, 46, 0.18)',
        admin: '0 8px 24px rgba(11, 59, 46, 0.3)',
        'home-gold': '0 4px 12px rgba(224, 178, 90, 0.35)',
      },
      backgroundImage: {
        'home-hero-overlay':
          'linear-gradient(100deg, rgba(11,59,46,0.94) 0%, rgba(11,59,46,0.80) 40%, rgba(31,111,84,0.45) 75%, rgba(31,111,84,0.20) 100%)',
        'home-hero-fade':
          'linear-gradient(180deg, rgba(0,0,0,0) 70%, rgba(6,30,23,0.45) 100%)',
      },
    },
  },
  plugins: [],
};
