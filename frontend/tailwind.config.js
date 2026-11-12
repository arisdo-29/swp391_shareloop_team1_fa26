import plugin from 'tailwindcss/plugin.js';
import { lightTokens, darkTokens, tokenVariables } from './theme-tokens.js';

const bottlePalette = {
  100: '#FFFFFF',
  200: '#F3F8F5',
  300: '#D8E6DF',
  500: '#1F6F54',
  700: '#1F6F54',
  800: '#0B3B2E',
  900: '#0B3B2E',
};

const tokenColor = (name) => 'rgb(var(--theme-' + name + ') / <alpha-value>)';
const groups = [
  'primary',
  'secondary',
  'surface',
  'text',
  'sage',
  'success',
  'warning',
  'error',
  'info',
];
const semanticColors = Object.fromEntries(
  Object.keys(lightTokens).map((name) => [name, tokenColor(name)]),
);
for (const group of groups) {
  semanticColors[group] = Object.fromEntries(
    Object.keys(lightTokens)
      .filter((name) => name === group || name.startsWith(group + '-'))
      .map((name) => [name === group ? 'DEFAULT' : name.slice(group.length + 1), tokenColor(name)]),
  );
}

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['selector', '[data-theme="dark"]'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Be Vietnam Pro', 'system-ui', 'sans-serif'] },
      colors: {
        ...semanticColors,
        admin: {
          sidebar: '#072e29',
          group: '#115740',
          divider: '#dce2d9',
          label: '#d3fc9c',
          text: '#f0f5ea',
        },
        home: {
          header: '#072e29',
          gold: '#d3fc9c',
          'gold-hover': '#e6ffca',
          ink: '#072e29',
          bronze: '#115740',
        },
        success: { ...bottlePalette, ...semanticColors.success },
      },
      fontSize: { '2xs': ['0.6875rem', { lineHeight: '1rem' }] },
      borderRadius: { sm: '8px', md: '12px', lg: '20px', xl: '24px' },
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
        'home-hero-fade': 'linear-gradient(180deg, rgba(0,0,0,0) 70%, rgba(6,30,23,0.45) 100%)',
      },
    },
  },
  plugins: [
    plugin(({ addBase }) =>
      addBase({
        ':root': tokenVariables(lightTokens),
        ':root[data-theme="dark"]': tokenVariables(darkTokens),
      }),
    ),
  ],
};
