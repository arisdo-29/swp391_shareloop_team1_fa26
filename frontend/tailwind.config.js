/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Be Vietnam Pro', 'system-ui', 'sans-serif'] },
      colors: {
        primary: {
          DEFAULT: '#145C4E',
          hover: '#0F4A3F',
          fixed: '#CDE4DC',
          soft: '#E7F1ED',
          faint: '#F2F7F5',
        },
        secondary: { DEFAULT: '#B85C35', hover: '#984629', fixed: '#F2D3C5', soft: '#F8EDE8' },
        background: '#F5F7F6',
        surface: { DEFAULT: '#FFFFFF', low: '#F0F3F2', container: '#E5EAE8', high: '#D8E0DD' },
        text: { primary: '#17211E', secondary: '#46534F', muted: '#5F6B66' },
        border: '#D7DEDB',
        success: { DEFAULT: '#277A55', soft: '#EAF4EE' },
        warning: { DEFAULT: '#8C5410', soft: '#FBF3E4' },
        error: { DEFAULT: '#B44444', soft: '#F9ECEC' },
        info: { DEFAULT: '#356A8A', soft: '#E9F1F6' },
      },
      fontSize: { '2xs': ['0.6875rem', { lineHeight: '1rem' }] },
      borderRadius: { sm: '6px', md: '10px', lg: '14px', xl: '18px' },
      boxShadow: {
        sm: '0 1px 2px rgba(20, 45, 38, 0.05)',
        md: '0 10px 30px rgba(20, 45, 38, 0.08)',
        lg: '0 24px 60px rgba(20, 45, 38, 0.12)',
      },
    },
  },
  plugins: [],
};
