export interface AuthToken {
  token: string;
  tokenType: string;
  expiresAt: string;
}

export interface Link {
  code: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
}

export interface PagedLinks {
  links: Link[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DailyClicks {
  day: string;
  clicks: number;
}

export interface ReferrerClicks {
  referrer: string;
  clicks: number;
}

export interface Stats {
  code: string;
  longUrl: string;
  totalClicks: number;
  uniqueVisitors: number;
  firstClickAt: string | null;
  lastClickAt: string | null;
  windowDays: number;
  clicksByDay: DailyClicks[];
  topReferrers: ReferrerClicks[];
}
