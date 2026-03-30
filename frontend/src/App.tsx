import SeatMap from './components/SeatMap';
import './App.css';

function App() {
  return (
    <div className="container mx-auto p-4 flex flex-col items-center justify-center min-h-screen">
      <header className="w-full text-center py-8">
        <h1 className="text-5xl font-bold tracking-tighter mb-2">BookMyShow</h1>
        <p className="text-lg opacity-80">Experience the world of cinema with real-time seat tracking.</p>
      </header>
      
      <main className="w-full max-w-4xl bg-white/5 backdrop-blur-xl border border-white/10 rounded-3xl p-10 shadow-2xl">
        <SeatMap showId={101} userId={789} />
      </main>

      <footer className="mt-12 text-sm opacity-50">
        &copy; 2026 BookMyShow Scalable Booking Demo
      </footer>
    </div>
  );
}

export default App;
