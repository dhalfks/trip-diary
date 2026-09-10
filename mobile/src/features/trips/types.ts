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

export type Place = {
  id: string; name: string; address: string | null; latitude: number | null; longitude: number | null;
  createdAt: string; updatedAt: string;
};
export type PlaceInput = Pick<Place, 'name' | 'address' | 'latitude' | 'longitude'>;
export type Itinerary = {
  id: string; tripDayId: string; title: string; notes: string | null; startTime: string | null;
  endTime: string | null; sortOrder: number; place: Place | null; createdAt: string; updatedAt: string;
};
export type ItineraryInput = { title: string; notes: string | null; startTime: string | null; endTime: string | null; placeId: string | null };
export type DiaryEntryLink = { id: string; name: string };
export type DiaryEntry = {
  id: string; tripDayId: string; title: string; content: string;
  itinerary: DiaryEntryLink | null; place: DiaryEntryLink | null;
  createdAt: string; updatedAt: string;
};
export type DiaryEntryInput = {
  title: string; content: string; itineraryId: string | null; placeId: string | null;
};
