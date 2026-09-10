import React, { forwardRef } from 'react';
import { twMerge } from 'tailwind-merge';

/*
 * Every variant pairs a token background with a token foreground.
 * `secondary` was 'bg-surface-3 text-white', and surface-3 is slate-200 in
 * light mode - white-on-light-grey, so its label was effectively invisible
 * until the theme happened to be dark. `danger` used raw red-600/red-700
 * rather than the danger token, so it ignored the theme entirely.
 */
const variantClasses = {
  primary: 'bg-primary text-fg-oncolor hover:bg-primary-hover focus:ring-primary',
  secondary: 'bg-surface-2 text-fg border border-border hover:bg-surface-3 focus:ring-primary',
  danger: 'bg-danger text-fg-oncolor hover:opacity-90 focus:ring-danger',
  outline: 'bg-surface text-fg border border-border hover:bg-surface-2 focus:ring-primary',
  ghost: 'bg-transparent text-fg-muted hover:bg-surface-2 hover:text-fg focus:ring-primary',
  link: 'bg-transparent text-primary hover:underline focus:ring-primary',
};

const sizeClasses = {
  xs: 'px-2.5 py-1.5 text-xs rounded',
  sm: 'px-3 py-2 text-sm leading-4 rounded-md',
  md: 'px-4 py-2 text-sm rounded-md',
  lg: 'px-4 py-2 text-base rounded-md',
  xl: 'px-6 py-3 text-base rounded-md',
};

const iconSizeClasses = {
  xs: 'h-3.5 w-3.5',
  sm: 'h-4 w-4',
  md: 'h-4 w-4',
  lg: 'h-5 w-5',
  xl: 'h-5 w-5',
};

const Button = forwardRef(
  (
    {
      variant = 'primary',
      size = 'md',
      isLoading = false,
      fullWidth = false,
      leftIcon: LeftIcon,
      rightIcon: RightIcon,
      children,
      className,
      disabled,
      onClick,
      ...props
    },
    ref
  ) => {
    const handleClick = e => {
      if (isLoading || disabled) {
        e.preventDefault();
        return;
      }
      onClick?.(e);
    };

    const buttonClasses = twMerge(
      'inline-flex items-center justify-center font-medium rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2',
      variantClasses[variant] || variantClasses.primary,
      sizeClasses[size] || sizeClasses.md,
      fullWidth && 'w-full',
      (isLoading || disabled) && 'opacity-50 cursor-not-allowed',
      className
    );

    const iconClasses = twMerge(iconSizeClasses[size] || iconSizeClasses.md, 'flex-shrink-0');

    return (
      <button
        ref={ref}
        type='button'
        className={buttonClasses}
        disabled={isLoading || disabled}
        onClick={handleClick}
        {...props}
      >
        {isLoading ? (
          <>
            <svg
              className={twMerge(iconClasses, 'animate-spin -ml-1 mr-2')}
              xmlns='http://www.w3.org/2000/svg'
              fill='none'
              viewBox='0 0 24 24'
            >
              <circle
                className='opacity-25'
                cx='12'
                cy='12'
                r='10'
                stroke='currentColor'
                strokeWidth='4'
              ></circle>
              <path
                className='opacity-75'
                fill='currentColor'
                d='M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z'
              ></path>
            </svg>
            {children}
          </>
        ) : (
          <>
            {LeftIcon && <span className={twMerge(iconClasses, 'mr-2')}>{LeftIcon}</span>}
            {children}
            {RightIcon && <span className={twMerge(iconClasses, 'ml-2')}>{RightIcon}</span>}
          </>
        )}
      </button>
    );
  }
);

Button.displayName = 'Button';

export default Button;
