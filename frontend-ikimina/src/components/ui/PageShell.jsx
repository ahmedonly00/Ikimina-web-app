import React from 'react';

/**
 * The standard page frame: own scroll area, themed background, centred column
 * with a side gutter.
 *
 * The dashboard shell is `fixed inset-0` with `overflow-hidden`, so a page
 * that does not provide its own scroll container cannot scroll, and one
 * without a max-width runs its content under the right edge of the viewport -
 * the super admin dashboard's fourth stat card was cut in half that way.
 *
 * `title` and `subtitle` are optional; pages with a custom header (an action
 * button in the header row, for instance) can render their own and pass only
 * children.
 */
const PageShell = ({ title, subtitle, actions, children }) => (
  <div className='h-full w-full overflow-y-auto bg-bg'>
    <div className='mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8'>
      {(title || actions) && (
        <header className='mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between'>
          <div>
            {title && <h1>{title}</h1>}
            {subtitle && <p className='mt-1 text-sm text-fg-muted'>{subtitle}</p>}
          </div>
          {actions}
        </header>
      )}
      {children}
    </div>
  </div>
);

export default PageShell;
