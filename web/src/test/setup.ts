import '@testing-library/jest-dom/vitest';

// jsdom implements no layout, so it has no ResizeObserver — and Recharts' ResponsiveContainer
// constructs one on mount. Without this stub, any test that renders a chart dies inside a React
// effect with an error that names Recharts rather than the missing browser API.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;
