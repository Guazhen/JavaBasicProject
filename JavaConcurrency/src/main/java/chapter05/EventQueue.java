package chapter05;

import java.util.LinkedList;

public class EventQueue {

    private final int max;

    static class Event{

    }

    private final LinkedList<Event> eventQueue = new LinkedList<>();
    private final static int DEAULT_MAX_EVENT=10;

    public EventQueue() {
        this(DEAULT_MAX_EVENT);
    }

    public EventQueue(int max){
        this.max = max;
    }

    public void offer(Event event) {
        synchronized (eventQueue) {
            if(eventQueue.size() >= max) {
                try {
                    System.out.println("the queue is full");
                    eventQueue.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("the new event is submitted");
            eventQueue.addLast(event);
            eventQueue.notify();
        }

    }

    public Event take() {
        synchronized (eventQueue) {
            if ( eventQueue.isEmpty()) {

                try {
                    System.out.println("the queue is empty");
                    eventQueue.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            }

            Event event = eventQueue.removeFirst();
            this.eventQueue.notify();
            System.out.println("the event " + event + " is handled");
            return event;
        }

    }



}
