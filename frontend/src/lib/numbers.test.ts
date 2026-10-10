import { formatDecimal } from './numbers';

describe('formatDecimal', () => {
  it.each([
    [10, '10'],
    [100, '100'],
    [0, '0'],
    ['10.0000', '10'],
    ['5.5000', '5.5'],
    ['4.50', '4.5'],
    [2.75, '2.75'],
    ['0.0000', '0'],
    [3, '3'],
  ])('%s is shown as %s', (input, shown) => {
    expect(formatDecimal(input)).toBe(shown);
  });
});
