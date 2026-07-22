/**
 * Converts numbers into formatted Vietnamese currency string and Vietnamese words.
 * Example: 20000000 -> "20.000.000 VND (Hai mươi triệu đồng)"
 */
export function formatVndAmountWithWords(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || isNaN(amount) || amount <= 0) {
    return '';
  }

  const formattedNum = new Intl.NumberFormat('vi-VN').format(amount);
  const words = numberToVietnameseWords(amount);
  
  if (words) {
    return `${formattedNum} VND (${words})`;
  }
  return `${formattedNum} VND`;
}

export function numberToVietnameseWords(n: number): string {
  if (!n || n <= 0 || !isFinite(n)) return '';

  const units = ['', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín'];
  const totalNum = Math.floor(n);
  const scales = ['', 'nghìn', 'triệu', 'tỷ', 'nghìn tỷ', 'triệu tỷ'];

  const groups: number[] = [];
  let temp = totalNum;
  while (temp > 0) {
    groups.push(temp % 1000);
    temp = Math.floor(temp / 1000);
  }

  if (groups.length === 0) return '';

  const resultWords: string[] = [];

  for (let i = groups.length - 1; i >= 0; i--) {
    const groupVal = groups[i];
    const scaleName = scales[i];

    if (groupVal === 0) {
      continue;
    }

    const hundred = Math.floor(groupVal / 100);
    const ten = Math.floor((groupVal % 100) / 10);
    const unit = groupVal % 10;

    let groupStr = '';

    if (hundred > 0) {
      groupStr += units[hundred] + ' trăm';
    } else if (i < groups.length - 1) {
      groupStr += 'không trăm';
    }

    if (ten > 1) {
      groupStr += (groupStr ? ' ' : '') + units[ten] + ' mươi';
      if (unit === 1) groupStr += ' mốt';
      else if (unit === 5) groupStr += ' lăm';
      else if (unit > 0) groupStr += ' ' + units[unit];
    } else if (ten === 1) {
      groupStr += (groupStr ? ' ' : '') + 'mười';
      if (unit === 5) groupStr += ' lăm';
      else if (unit > 0) groupStr += ' ' + units[unit];
    } else if (unit > 0) {
      if (hundred > 0 || i < groups.length - 1) {
        groupStr += (groupStr ? ' ' : '') + 'lẻ ' + units[unit];
      } else {
        groupStr += (groupStr ? ' ' : '') + units[unit];
      }
    }

    if (groupStr) {
      if (scaleName) {
        groupStr += ' ' + scaleName;
      }
      resultWords.push(groupStr);
    }
  }

  if (!resultWords.length) return '';
  let full = resultWords.join(' ').trim().replace(/\s+/g, ' ');
  return full.charAt(0).toUpperCase() + full.slice(1) + ' đồng';
}
