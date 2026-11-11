// Shared by Tailwind utilities and legacy CSS aliases.
export const lightTokens = {
  'brand-forest': '#072e29',
  'brand-green': '#115740',
  'brand-text': '#f7f7f3',
  primary: '#072e29',
  'primary-hover': '#115740',
  'primary-fixed': '#dce9d5',
  'primary-soft': '#eef4e8',
  'primary-faint': '#eef4e8',
  secondary: '#072e29',
  'secondary-hover': '#115740',
  'secondary-fixed': '#dce9d5',
  'secondary-soft': '#eef4e8',
  accent: '#115740',
  link: '#115740',
  highlight: '#d3fc9c',
  sage: '#dce2d9',
  'sage-light': '#f7f7f3',
  background: '#f7f7f3',
  surface: '#ffffff',
  'surface-low': '#f7f7f3',
  'surface-container': '#eff1e9',
  'surface-high': '#e4ebdc',
  'surface-hover': '#eef4e8',
  'text-primary': '#102e27',
  'text-secondary': '#4e6158',
  'text-muted': '#647268',
  'text-description': '#4e6158',
  border: '#dce2d9',
  success: '#236344',
  'success-soft': '#eaf4e5',
  warning: '#805714',
  'warning-soft': '#faf2de',
  error: '#ad3535',
  'error-soft': '#fbecec',
  info: '#365f73',
  'info-soft': '#eaf1f7',
  'on-primary': '#ffffff',
  'on-highlight': '#072e29',
  'highlight-hover': '#e6ffca',
};

export const darkTokens = {
  ...lightTokens,
  primary: '#b9ed9a',
  'primary-hover': '#d3fc9c',
  'primary-fixed': '#344f3c',
  'primary-soft': '#243d30',
  'primary-faint': '#243d30',
  secondary: '#b9ed9a',
  'secondary-hover': '#d3fc9c',
  'secondary-fixed': '#344f3c',
  'secondary-soft': '#243d30',
  accent: '#b9ed9a',
  link: '#d3fc9c',
  sage: '#344f3c',
  'sage-light': '#182b24',
  background: '#101d19',
  surface: '#192b24',
  'surface-low': '#14231d',
  'surface-container': '#24382e',
  'surface-high': '#304939',
  'surface-hover': '#2a4233',
  'text-primary': '#edf3e8',
  'text-secondary': '#c1d0c3',
  'text-muted': '#a4b5a7',
  'text-description': '#c1d0c3',
  border: '#3b5143',
  success: '#b9ed9a',
  'success-soft': '#243d30',
  warning: '#edcc87',
  'warning-soft': '#3c3220',
  error: '#ffb3b3',
  'error-soft': '#422929',
  info: '#accfe5',
  'info-soft': '#223744',
  'on-primary': '#102e27',
};

export function tokenVariables(tokens) {
  return Object.fromEntries(
    Object.entries(tokens).flatMap(([name, hex]) => {
      const channels = hex
        .slice(1)
        .match(/../g)
        .map((part) => parseInt(part, 16))
        .join(' ');
      return [
        [`--theme-${name}`, channels],
        [`--color-${name}`, `rgb(var(--theme-${name}))`],
      ];
    }),
  );
}
