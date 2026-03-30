import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import { motion } from 'framer-motion';
import { Check, Loader2 } from 'lucide-react';
import { useWebSocket, type SeatStatusEvent } from '../hooks/useWebSocket';

interface Seat {
    dbId: number;
    id: string;
    number: string;
    status: 'AVAILABLE' | 'LOCKED' | 'BOOKED' | 'SELECTED';
}

const SeatMap: React.FC<{ showId: number; userId: number }> = ({ showId, userId }) => {
    const [seats, setSeats] = useState<Seat[]>([]);
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState('');
    const { lastEvent } = useWebSocket(showId);

    // Initial load - fetch true state from backend
    useEffect(() => {
        const fetchSeats = async () => {
            try {
                const response = await axios.get(`http://localhost:8080/api/shows/${showId}/seats`);
                const mappedSeats: Seat[] = response.data.map((s: any) => ({
                    dbId: s.id,
                    id: s.seatNumber,
                    number: s.seatNumber,
                    status: s.status
                }));
                setSeats(mappedSeats);
            } catch (error) {
                console.error("Failed to fetch seats:", error);
                setMessage("Failed to load map");
            } finally {
                setLoading(false);
            }
        };
        fetchSeats();
    }, [showId]);

    // Process real-time updates
    useEffect(() => {
        if (lastEvent) {
            setSeats(prev => prev.map(seat => {
                if (lastEvent.seatNumbers.includes(seat.number)) {
                    // Don't override if user has it SELECTED locally unless it's BOOKED
                    if (seat.status === 'SELECTED' && lastEvent.status === 'LOCKED') return seat;
                    return { ...seat, status: lastEvent.status };
                }
                return seat;
            }));
        }
    }, [lastEvent]);

    const handleSeatClick = async (seat: Seat) => {
        if (seat.status !== 'AVAILABLE') return;

        // Optimistically set to SELECTED locally
        setSeats(prev => prev.map(s => s.id === seat.id ? { ...s, status: 'SELECTED' } : s));

        try {
            await axios.post(`http://localhost:8080/api/shows/${showId}/hold`, {
                seatNumbers: [seat.number],
                userId: userId
            });
            setMessage('Seat held! Confirm within 10 mins.');
            setTimeout(() => setMessage(''), 3000);
        } catch (error: any) {
            setSeats(prev => prev.map(s => s.id === seat.id ? { ...s, status: 'AVAILABLE' } : s));
            setMessage(error.response?.data || 'Failed to lock seat');
            setTimeout(() => setMessage(''), 3000);
        }
    };

    const handleConfirmSelection = async () => {
        const selectedSeats = seats.filter(s => s.status === 'SELECTED');
        if (selectedSeats.length === 0) {
            setMessage('Please select a seat first!');
            return;
        }

        try {
            await axios.post(`http://localhost:8080/api/shows/${showId}/confirm`, {
                userId: userId,
                seatDatabaseIds: selectedSeats.map(s => s.dbId),
                seatNumbers: selectedSeats.map(s => s.number)
            });
            
            // Success! Update local state to BOOKED
            setSeats(prev => prev.map(s => 
                s.status === 'SELECTED' ? { ...s, status: 'BOOKED' } : s
            ));
            setMessage('Booking Guaranteed! Check your email.');
        } catch (error: any) {
            setMessage(error.response?.data || 'Booking failed. Try again.');
        }
    };

    const groupedSeats = useMemo(() => {
        const rows: Record<string, Seat[]> = {};
        seats.forEach(s => {
            const row = s.number[0];
            if (!rows[row]) rows[row] = [];
            rows[row].push(s);
        });
        return rows;
    }, [seats]);

    if (loading) return <div className="flex items-center justify-center h-64"><Loader2 className="animate-spin text-purple-600" /></div>;

    return (
        <div className="seat-selection-container">
            <header className="mb-8 text-center">
                <h2 className="text-3xl font-bold mb-2 text-white">Select Your Seats</h2>
                <div className="screen-indicator">SCREEN THIS WAY</div>
            </header>

            <div className="seat-grid">
                {Object.entries(groupedSeats).map(([row, rowSeats]) => (
                    <div key={row} className="seat-row">
                        <span className="row-label">{row}</span>
                        <div className="seats">
                            {rowSeats.map(seat => (
                                <motion.div
                                    key={seat.id}
                                    whileHover={seat.status === 'AVAILABLE' ? { scale: 1.2 } : {}}
                                    whileTap={seat.status === 'AVAILABLE' ? { scale: 0.9 } : {}}
                                    className={`seat ${seat.status.toLowerCase()}`}
                                    onClick={() => handleSeatClick(seat)}
                                >
                                    {seat.status === 'SELECTED' && <Check size={12} strokeWidth={3} />}
                                </motion.div>
                            ))}
                        </div>
                    </div>
                ))}
            </div>

            <footer className="mt-12">
                <div className="legend">
                    <div className="legend-item"><div className="seat available"></div> Available</div>
                    <div className="legend-item"><div className="seat locked"></div> Held</div>
                    <div className="legend-item"><div className="seat booked"></div> Sold</div>
                    <div className="legend-item"><div className="seat selected"></div> Your Choice</div>
                </div>

                {message && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }} 
                        animate={{ opacity: 1, y: 0 }}
                        className="status-message bg-purple-600/20 text-purple-200 border border-purple-500/30 p-4 rounded-xl mb-6 text-center"
                    >
                        {message}
                    </motion.div>
                )}
                
                <button 
                    onClick={handleConfirmSelection}
                    className="confirm-btn active:scale-95 transition-transform"
                >
                    Confirm Selection
                </button>
            </footer>
        </div>
    );
};

export default SeatMap;
