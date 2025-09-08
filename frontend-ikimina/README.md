# Ikimina Frontend

This is the frontend for the Ikimina application, built with React, TypeScript, Redux Toolkit, and Tailwind CSS.

## Getting Started

### Prerequisites

- Node.js (v16 or higher)
- npm (v8 or higher) or yarn

### Installation

1. Install dependencies:
   ```bash
   npm install
   # or
   yarn
   ```

2. Create a `.env` file in the root directory with the following variables:
   ```
   VITE_API_URL=http://localhost:8080/api
   VITE_APP_ENV=development
   ```

### Available Scripts

- `npm run dev` - Start the development server
- `npm run build` - Build the application for production
- `npm run preview` - Preview the production build locally
- `npm run lint` - Run ESLint
- `npm run format` - Format code with Prettier

## Project Structure

```
src/
├── app/                 # Redux store and API configuration
├── components/          # Reusable UI components
├── features/            # Feature-based modules
│   └── auth/            # Authentication feature
├── layouts/             # Layout components
├── utils/               # Utility functions
├── App.tsx              # Main application component
└── main.tsx             # Application entry point
```

## Styling

This project uses Tailwind CSS for styling. The main styles are defined in `src/index.css`.

## State Management

State is managed using Redux Toolkit with RTK Query for API calls. The store is configured in `src/app/store.ts`.

## API Integration

API calls are handled using RTK Query. The API slice is defined in `src/app/api/apiSlice.ts`.

## Routing

Routing is handled by React Router. The main routes are defined in `src/App.tsx`.

## Environment Variables

- `VITE_API_URL` - Base URL for the API
- `VITE_APP_ENV` - Current environment (development, production, test)

## Deployment

To deploy the application, run:

```bash
npm run build
```

This will create a `dist` directory with the production build.
