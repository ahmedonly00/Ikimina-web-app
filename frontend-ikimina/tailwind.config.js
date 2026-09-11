/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      /*
       * Semantic colours, not palette values.
       *
       * Each maps to a CSS custom property defined in index.css, once for
       * light and once for .dark. Components say `bg-surface` rather than
       * `bg-white`, so one class on <html> flips the entire theme.
       *
       * The previous approach overrode utilities (`.dark .bg-white { ... }`)
       * for about forty hand-listed classes. Anything not on that list stayed
       * light, which is why dark mode looked half-applied.
       *
       * Channels are stored as raw RGB triplets so Tailwind's opacity
       * modifiers keep working: bg-primary/10, border-border/60, and so on.
       */
      colors: {
        bg: 'rgb(var(--c-bg) / <alpha-value>)',
        surface: 'rgb(var(--c-surface) / <alpha-value>)',
        'surface-2': 'rgb(var(--c-surface-2) / <alpha-value>)',
        'surface-3': 'rgb(var(--c-surface-3) / <alpha-value>)',
        border: 'rgb(var(--c-border) / <alpha-value>)',
        'border-strong': 'rgb(var(--c-border-strong) / <alpha-value>)',

        fg: 'rgb(var(--c-fg) / <alpha-value>)',
        'fg-muted': 'rgb(var(--c-fg-muted) / <alpha-value>)',
        'fg-subtle': 'rgb(var(--c-fg-subtle) / <alpha-value>)',
        'fg-oncolor': 'rgb(var(--c-fg-oncolor) / <alpha-value>)',

        primary: {
          DEFAULT: 'rgb(var(--c-primary) / <alpha-value>)',
          hover: 'rgb(var(--c-primary-hover) / <alpha-value>)',
          subtle: 'rgb(var(--c-primary-subtle) / <alpha-value>)',
        },
        success: {
          DEFAULT: 'rgb(var(--c-success) / <alpha-value>)',
          subtle: 'rgb(var(--c-success-subtle) / <alpha-value>)',
        },
        warning: {
          DEFAULT: 'rgb(var(--c-warning) / <alpha-value>)',
          subtle: 'rgb(var(--c-warning-subtle) / <alpha-value>)',
        },
        danger: {
          DEFAULT: 'rgb(var(--c-danger) / <alpha-value>)',
          subtle: 'rgb(var(--c-danger-subtle) / <alpha-value>)',
        },
        info: {
          DEFAULT: 'rgb(var(--c-info) / <alpha-value>)',
          subtle: 'rgb(var(--c-info-subtle) / <alpha-value>)',
        },

        /* Sidebar reads as a distinct surface in both themes. */
        sidebar: {
          DEFAULT: 'rgb(var(--c-sidebar) / <alpha-value>)',
          hover: 'rgb(var(--c-sidebar-hover) / <alpha-value>)',
          active: 'rgb(var(--c-sidebar-active) / <alpha-value>)',
          fg: 'rgb(var(--c-sidebar-fg) / <alpha-value>)',
          'fg-muted': 'rgb(var(--c-sidebar-fg-muted) / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: [
          'Inter var',
          'Inter',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
        /* Tabular figures keep money columns aligned. */
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      borderRadius: {
        card: '0.75rem',
      },
      boxShadow: {
        /* Softer and lower-contrast than Tailwind defaults, which look heavy
           on a dark surface. */
        card: '0 1px 2px 0 rgb(0 0 0 / 0.04), 0 1px 3px 0 rgb(0 0 0 / 0.06)',
        'card-hover': '0 4px 6px -1px rgb(0 0 0 / 0.07), 0 2px 4px -2px rgb(0 0 0 / 0.05)',
        popover: '0 10px 24px -6px rgb(0 0 0 / 0.18)',
      },
      transitionDuration: {
        DEFAULT: '150ms',
      },
    },
  },
  plugins: [],
};
