import { Redirect } from 'expo-router';
import { useAuth } from '@/features/auth/auth-context';

export default function IndexScreen() {
  return <Redirect href={useAuth().isAuthenticated ? '/home' : '/login'} />;
}
