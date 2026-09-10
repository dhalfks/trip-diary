export type TripDay = { id: string; date: string; dayNumber: number };
export type Trip = {
  id: string; title: string; startDate: string; endDate: string; timezone: string;
  days: TripDay[]; createdAt: string; updatedAt: string;
};
export type TripInput = Pick<Trip, 'title' | 'startDate' | 'endDate' | 'timezone'>;
export type TripPage = {
  content: Trip[]; page: number; size: number; totalElements: number;
  totalPages: number; first: boolean; last: boolean;
};
