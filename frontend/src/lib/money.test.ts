import { formatRwf, isValidAmount, toWireAmount } from './money';

describe('money', () => {
  it.each([
    ['150000.00', '150,000 RWF'],
    ['0.00', '0 RWF'],
    ['1234567.50', '1,234,567.5 RWF'],
    ['-2500.00', '-2,500 RWF'],
    ['999', '999 RWF'],
    ['99999999999999999.99', '99,999,999,999,999,999.99 RWF'],
  ])('formats %s as %s without floating point', (amount, shown) => {
    expect(formatRwf(amount)).toBe(shown);
  });

  it('keeps digits a float would lose', () => {
    // As a JS number this is 10000000000000000 - the last digit would silently change.
    expect(formatRwf('9999999999999999.00')).toBe('9,999,999,999,999,999 RWF');
  });

  it.each(['5000', '5,000', ' 5 000 ', '5000.5', '5000.50'])('accepts typed amount %s', (input) => {
    expect(toWireAmount(input)).toMatch(/^5000\.(00|50)$/);
  });

  it.each(['', 'abc', '1e3', '5000.005', '-', '0x10', '01'])('rejects %j', (input) => {
    expect(toWireAmount(input)).toBeNull();
    expect(isValidAmount(input)).toBe(false);
  });
});
