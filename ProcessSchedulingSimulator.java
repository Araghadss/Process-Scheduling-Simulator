import java.util.*;

public class ProcessSchedulingSimulator {

    // تعريف كلاس العملية
    static class Process implements Comparable<Process> {
        String pid;
        int arrival;
        int burst;
        int remaining;
        int startTime = -1;
        int finishTime = -1;

        public Process(String pid, int arrival, int burst) {
            this.pid = pid;
            this.arrival = arrival;
            this.burst = burst;
            this.remaining = burst;
        }

        // المقارنة: نختار أولاً الأقل زمن متبقٍ، وفي حالة التعادل نستخدم زمن الوصول (FCFS)
        @Override
        public int compareTo(Process other) {
            if (this.remaining == other.remaining) {
                return this.arrival - other.arrival;
            }
            return this.remaining - other.remaining;
        }
    }

    // تعريف كلاس الحدث (Event)
    static class Event implements Comparable<Event> {
        int time;
        String eventType; // يمكن أن يكون "arrival"
        Process process;

        public Event(int time, String eventType, Process process) {
            this.time = time;
            this.eventType = eventType;
            this.process = process;
        }

        @Override
        public int compareTo(Event other) {
            return this.time - other.time;
        }
    }

    // كلاس لتسجيل فترات التنفيذ (لعمل Gantt Chart)
    static class GanttEvent {
        int start;
        int end;
        String label;

        public GanttEvent(int start, int end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }

        @Override
        public String toString() {
            return start + "-" + end + " " + label;
        }
    }

    // كلاس لتجميع ناتج المحاكاة
    static class SimulationResult {
        List<GanttEvent> ganttChart;
        double avgTurnaround;
        double avgWaiting;
        double cpuUtil;
        int totalTime;

        public SimulationResult(List<GanttEvent> ganttChart, double avgTurnaround, double avgWaiting, double cpuUtil, int totalTime) {
            this.ganttChart = ganttChart;
            this.avgTurnaround = avgTurnaround;
            this.avgWaiting = avgWaiting;
            this.cpuUtil = cpuUtil;
            this.totalTime = totalTime;
        }
    }

    // دالة المحاكاة بنهج event-driven
    public static SimulationResult eventDrivenSimulation(List<Process> processes, int contextSwitchTime) {
        // إنشاء قائمة أحداث لعمليات الوصول (arrival events)
        PriorityQueue<Event> eventQueue = new PriorityQueue<>();
        for (Process p : processes) {
            eventQueue.offer(new Event(p.arrival, "arrival", p));
        }
        // قائمة الانتظار الجاهزة (ready queue)
        PriorityQueue<Process> readyQueue = new PriorityQueue<>();
        List<GanttEvent> ganttChart = new ArrayList<>();

        int currentTime = 0;
        Process currentProcess = null;
        int completed = 0;
        int n = processes.size();

        while (completed < n || currentProcess != null || !readyQueue.isEmpty()) {
            // إذا لم تكن هناك عملية جارية وقائمة الانتظار فارغة، نقفز لوقت الحدث التالي
            if (currentProcess == null && readyQueue.isEmpty() && !eventQueue.isEmpty()) {
                Event nextEvent = eventQueue.poll();
                currentTime = nextEvent.time;
                readyQueue.offer(nextEvent.process);
            }

            // معالجة كافة أحداث الوصول التي حانت حتى currentTime
            while (!eventQueue.isEmpty() && eventQueue.peek().time <= currentTime) {
                Event ev = eventQueue.poll();
                readyQueue.offer(ev.process);
            }

            // إذا لم توجد عملية جارية، نختار من قائمة الانتظار مع إجراء تبديل سياق إذا لم يكن هذا بداية التشغيل
            if (currentProcess == null && !readyQueue.isEmpty()) {
                if (!ganttChart.isEmpty() && !ganttChart.get(ganttChart.size()-1).label.equals("CS")) {
                    int csStart = currentTime;
                    currentTime += contextSwitchTime;
                    ganttChart.add(new GanttEvent(csStart, currentTime, "CS"));
                }
                currentProcess = readyQueue.poll();
                if (currentProcess.startTime == -1) {
                    currentProcess.startTime = currentTime;
                }
            }

            if (currentProcess != null) {
                // تحديد وقت وصول الحدث القادم (إن وجد)
                int nextArrivalTime = eventQueue.isEmpty() ? Integer.MAX_VALUE : eventQueue.peek().time;
                int potentialFinishTime = currentTime + currentProcess.remaining;
                if (nextArrivalTime < potentialFinishTime) {
                    // تنفيذ العملية حتى وصول حدث جديد
                    int runInterval = nextArrivalTime - currentTime;
                    ganttChart.add(new GanttEvent(currentTime, nextArrivalTime, currentProcess.pid));
                    currentProcess.remaining -= runInterval;
                    currentTime = nextArrivalTime;

                    // إضافة العمليات التي وصلت في هذا الوقت
                    while (!eventQueue.isEmpty() && eventQueue.peek().time <= currentTime) {
                        Event ev = eventQueue.poll();
                        readyQueue.offer(ev.process);
                    }

                    // فحص التمهيد: إذا كانت العملية في قائمة الانتظار تمتلك زمن متبقٍ أقل من العملية الحالية
                    if (!readyQueue.isEmpty() && readyQueue.peek().remaining < currentProcess.remaining) {
                        int csStart = currentTime;
                        currentTime += contextSwitchTime;
                        ganttChart.add(new GanttEvent(csStart, currentTime, "CS"));
                        readyQueue.offer(currentProcess);
                        currentProcess = null;
                    }
                } else {
                    // لا توجد وصولات جديدة قبل انتهاء العملية الحالية
                    ganttChart.add(new GanttEvent(currentTime, potentialFinishTime, currentProcess.pid));
                    currentTime = potentialFinishTime;
                    currentProcess.finishTime = currentTime;
                    currentProcess.remaining = 0;
                    completed++;
                    currentProcess = null;
                }
            }
        }

        // حساب المقاييس النهائية
        int totalBurstTime = 0;
        double totalTurnaround = 0;
        double totalWaiting = 0;
        for (Process p : processes) {
            totalBurstTime += p.burst;
            int turnaround = p.finishTime - p.arrival;
            int waiting = turnaround - p.burst;
            totalTurnaround += turnaround;
            totalWaiting += waiting;
        }
        double avgTurnaround = totalTurnaround / n;
        double avgWaiting = totalWaiting / n;
        double cpuUtil = ((double) totalBurstTime / currentTime) * 100;

        return new SimulationResult(ganttChart, avgTurnaround, avgWaiting, cpuUtil, currentTime);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Process Scheduling Simulator");
        System.out.print("Enter number of processes: ");
        int n = scanner.nextInt();
        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            System.out.println("\nEnter details for Process P" + (i + 1) + ":");
            System.out.print("Arrival time (ms): ");
            int arrival = scanner.nextInt();
            System.out.print("Burst time (ms): ");
            int burst = scanner.nextInt();
            processes.add(new Process("P" + (i + 1), arrival, burst));
        }

        // يمكن فرز العمليات حسب زمن الوصول إذا لزم الأمر
        processes.sort(Comparator.comparingInt(p -> p.arrival));

        SimulationResult result = eventDrivenSimulation(processes, 1);

        // عرض مخطط جانت (Gantt Chart)
        System.out.println("\nGantt Chart:");
        for (GanttEvent ge : result.ganttChart) {
            System.out.println(ge);
        }
        System.out.println("\nPerformance Metrics:");
        System.out.printf("Average Turnaround Time: %.2f%n", result.avgTurnaround);
        System.out.printf("Average Waiting Time: %.2f%n", result.avgWaiting);
        System.out.printf("CPU Utilization: %.2f%%%n", result.cpuUtil);

        // عرض تفاصيل انتهاء كل عملية
        System.out.println("\nProcess Completion Details:");
        for (Process p : processes) {
            int turnaround = p.finishTime - p.arrival;
            int waiting = turnaround - p.burst;
            System.out.println(p.pid + ": Start = " + p.startTime + ", Finish = " + p.finishTime 
                    + ", Turnaround = " + turnaround + ", Waiting = " + waiting);
        }

        scanner.close();
    }
}