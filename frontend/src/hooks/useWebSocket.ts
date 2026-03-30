import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface SeatStatusEvent {
    seatNumbers: string[];
    status: 'AVAILABLE' | 'LOCKED' | 'BOOKED';
}

export const useWebSocket = (showId: number) => {
    const [lastEvent, setLastEvent] = useState<SeatStatusEvent | null>(null);
    const stompClient = useRef<Client | null>(null);

    useEffect(() => {
        const socket = new SockJS('http://localhost:8080/ws');
        const client = new Client({
            webSocketFactory: () => socket,
            onConnect: () => {
                console.log('Connected to WebSocket');
                client.subscribe(`/topic/shows/${showId}/seats`, (message) => {
                    const event: SeatStatusEvent = JSON.parse(message.body);
                    setLastEvent(event);
                });
            },
            onStompError: (frame) => {
                console.error('Broker reported error: ' + frame.headers['message']);
                console.error('Additional details: ' + frame.body);
            },
        });

        client.activate();
        stompClient.current = client;

        return () => {
            if (stompClient.current) {
                stompClient.current.deactivate();
            }
        };
    }, [showId]);

    return { lastEvent };
};
