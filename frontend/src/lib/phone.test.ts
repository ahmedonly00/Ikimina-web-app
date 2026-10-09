import { displayPhone, normalisePhone } from './phone';

describe('phone numbers (mirrors the backend PhoneNumber)', () => {
  it.each(['0788123456', '0788 123 456', '250788123456', '+250788123456', '+250 788-123-456', '(078) 812.3456'])(
    'normalises %j',
    (raw) => {
      expect(normalisePhone(raw)).toBe('+250788123456');
    },
  );

  it.each(['', '0788', '+15550100', '0688123456', '07881234567', 'phone'])('rejects %j', (raw) => {
    expect(normalisePhone(raw)).toBeNull();
  });

  it('shows numbers the local way', () => {
    expect(displayPhone('+250788123456')).toBe('0788 123 456');
    expect(displayPhone(null)).toBe('');
  });
});
