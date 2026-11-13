/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        farm: {
          green: '#16a34a',
          emerald: '#059669',
          earth: '#78350f',
          gold: '#eab308',
          dark: '#0f172a'
        }
      }
    }
  },
  plugins: []
};
