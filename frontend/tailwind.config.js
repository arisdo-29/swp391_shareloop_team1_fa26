const emeraldForest = {
  100: '#FFFFFF',
  200: '#D7E9E1',
  300: '#B4DAC9',
  500: '#31936B',
  700: '#176B4B',
  800: '#0B3F30',
};

/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Be Vietnam Pro', 'system-ui', 'sans-serif'] },
      colors: {
        primary: {
          DEFAULT: emeraldForest[700],
          hover: emeraldForest[800],
          fixed: emeraldForest[200],
          soft: emeraldForest[100],
          faint: emeraldForest[200],
        },
        secondary: {
          DEFAULT: emeraldForest[800],
          hover: emeraldForest[700],
          fixed: emeraldForest[200],
          soft: emeraldForest[100],
        },
        accent: emeraldForest[500],
        highlight: emeraldForest[300],
        sage: { DEFAULT: emeraldForest[300], light: emeraldForest[200] },
        background: emeraldForest[200],
        surface: {
          DEFAULT: emeraldForest[100],
          low: emeraldForest[100],
          container: emeraldForest[200],
          high: emeraldForest[500],
        },
        text: {
          primary: emeraldForest[800],
          secondary: emeraldForest[800],
          muted: emeraldForest[800],
        },
        border: emeraldForest[300],
        success: {
          ...emeraldForest,
          DEFAULT: emeraldForest[700],
          soft: emeraldForest[100],
        },
        warning: { DEFAULT: '#8A5B17', soft: '#F8F1DF' },
        error: { DEFAULT: '#B04444', soft: '#F8EAEA' },
        info: { DEFAULT: '#3F6475', soft: '#EAF0F1' },
      },
      fontSize: { '2xs': ['0.6875rem', { lineHeight: '1rem' }] },
      borderRadius: { sm: '6px', md: '10px', lg: '14px', xl: '18px' },
      boxShadow: {
        sm: '0 1px 2px rgba(11, 63, 48, 0.08)',
        md: '0 10px 30px rgba(11, 63, 48, 0.12)',
        lg: '0 24px 60px rgba(11, 63, 48, 0.18)',
      },
    },
  },
  plugins: [],
};
