import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Console Hotel Room Booking App
 * Single file for easy compilation in interviews
 */
public class HotelApp {

    // -------------- Domain --------------
    enum RoomType {
        SINGLE("Single", new BigDecimal("2000.00"), new BigDecimal("1.0")),
        DOUBLE("Double", new BigDecimal("2000.00"), new BigDecimal("1.2")),
        SUITE("Suite",  new BigDecimal("2000.00"), new BigDecimal("1.6"));

        final String label;
        final BigDecimal base;
        final BigDecimal multiplier;

        RoomType(String label, BigDecimal base, BigDecimal multiplier) {
            this.label = label;
            this.base = base;
            this.multiplier = multiplier;
        }

        BigDecimal nightlyRate() {
            return base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }

        @Override public String toString() { return label; }
    }

    enum RoomStatus { VACANT, OCCUPIED, OUT_OF_SERVICE }

    static class Room {
        final int number;
        final RoomType type;
        RoomStatus status = RoomStatus.VACANT;

        Room(int number, RoomType type) {
            this.number = number;
            this.type = type;
        }
    }

    enum BookingStatus { RESERVED, IN_HOUSE, COMPLETED, CANCELLED }

    static class Booking {
        final int id;
        final int roomNumber;
        final String guestName;
        final LocalDate checkIn;   // first night
        final LocalDate checkOut;  // morning after last night
        BookingStatus status = BookingStatus.RESERVED;

        // computed at creation time
        final BigDecimal nightlyRate;
        final long nights;
        final BigDecimal roomCharge;
        final BigDecimal tax;
        final BigDecimal total;

        Booking(int id, int roomNumber, String guestName, LocalDate checkIn,
                LocalDate checkOut, BigDecimal nightlyRate, BigDecimal taxRate) {
            this.id = id;
            this.roomNumber = roomNumber;
            this.guestName = guestName;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.nightlyRate = nightlyRate.setScale(2, RoundingMode.HALF_UP);
            this.nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
            this.roomCharge = this.nightlyRate.multiply(new BigDecimal(nights))
                    .setScale(2, RoundingMode.HALF_UP);
            this.tax = this.roomCharge.multiply(taxRate)
                    .setScale(2, RoundingMode.HALF_UP);
            this.total = this.roomCharge.add(this.tax)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    // -------------- Storage --------------
    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Map<Integer, Booking> bookings = new LinkedHashMap<>();
    private int nextBookingId = 1001;

    // -------------- Config --------------
    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // -------------- App entry --------------
    public static void main(String[] args) {
        HotelApp app = new HotelApp();
        app.seedSampleRooms();
        app.menuLoop();
    }

    private void seedSampleRooms() {
        // Ten rooms total
        rooms.put(101, new Room(101, RoomType.SINGLE));
        rooms.put(102, new Room(102, RoomType.SINGLE));
        rooms.put(103, new Room(103, RoomType.SINGLE));
        rooms.put(201, new Room(201, RoomType.DOUBLE));
        rooms.put(202, new Room(202, RoomType.DOUBLE));
        rooms.put(203, new Room(203, RoomType.DOUBLE));
        rooms.put(301, new Room(301, RoomType.SUITE));
        rooms.put(302, new Room(302, RoomType.SUITE));
        rooms.put(303, new Room(303, RoomType.SUITE));
        rooms.put(304, new Room(304, RoomType.SUITE));
    }

    // -------------- Menu --------------
    private final Scanner sc = new Scanner(System.in);

    private void menuLoop() {
        while (true) {
            System.out.println();
            System.out.println("Hotel Room Booking Console App");
            System.out.println("1 List rooms");
            System.out.println("2 Search available rooms");
            System.out.println("3 Create booking");
            System.out.println("4 View booking by id");
            System.out.println("5 Cancel booking");
            System.out.println("6 Check in");
            System.out.println("7 Check out and print bill");
            System.out.println("8 Exit");
            int choice = readInt("Choose an option", 1, 8);
            switch (choice) {
                case 1 -> listRooms();
                case 2 -> searchAvailable();
                case 3 -> createBookingFlow();
                case 4 -> viewBooking();
                case 5 -> cancelBooking();
                case 6 -> checkInFlow();
                case 7 -> checkOutFlow();
                case 8 -> {
                    System.out.println("Goodbye");
                    return;
                }
            }
        }
    }

    // -------------- Features --------------
    private void listRooms() {
        System.out.println("Rooms");
        System.out.println("Number  Type    Status  Rate");
        for (Room r : rooms.values()) {
            System.out.printf("%-7d %-7s %-8s %8s%n",
                    r.number, r.type, r.status, money(r.type.nightlyRate()));
        }
    }

    private void searchAvailable() {
        RoomType type = pickRoomType();
        LocalDate in = readDate("Enter check in date yyyy mm dd");
        LocalDate out = readDate("Enter check out date yyyy mm dd");
        if (!validateDates(in, out)) return;

        List<Room> free = findAvailableRooms(type, in, out);
        if (free.isEmpty()) {
            System.out.println("No rooms available for given dates and type");
        } else {
            System.out.println("Available rooms");
            for (Room r : free) {
                System.out.printf("Room %d %s Rate %s%n",
                        r.number, r.type, money(r.type.nightlyRate()));
            }
        }
    }

    private void createBookingFlow() {
        RoomType type = pickRoomType();
        LocalDate in = readDate("Enter check in date yyyy mm dd");
        LocalDate out = readDate("Enter check out date yyyy mm dd");
        if (!validateDates(in, out)) return;

        List<Room> free = findAvailableRooms(type, in, out);
        if (free.isEmpty()) {
            System.out.println("No rooms available. Try different dates or type");
            return;
        }
        System.out.println("Pick a room number from the available list");
        for (Room r : free) {
            System.out.printf("Room %d %s Rate %s%n",
                    r.number, r.type, money(r.type.nightlyRate()));
        }
        int chosen = readInt("Room number", free.stream().map(r -> r.number).toList());
        Room room = rooms.get(chosen);
        String guest = readNonEmpty("Enter guest full name");

        int id = nextBookingId++;
        Booking b = new Booking(id, room.number, guest, in, out, room.type.nightlyRate(), TAX_RATE);
        bookings.put(id, b);
        System.out.println("Booking created with id " + id);
        printBooking(b);
    }

    private void viewBooking() {
        int id = readInt("Enter booking id", 1, Integer.MAX_VALUE);
        Booking b = bookings.get(id);
        if (b == null) {
            System.out.println("No booking found");
            return;
        }
        printBooking(b);
    }

    private void cancelBooking() {
        int id = readInt("Enter booking id", 1, Integer.MAX_VALUE);
        Booking b = bookings.get(id);
        if (b == null) { System.out.println("No booking found"); return; }
        if (b.status != BookingStatus.RESERVED) {
            System.out.println("Only reserved bookings can be cancelled");
            return;
        }
        b.status = BookingStatus.CANCELLED;
        System.out.println("Booking cancelled");
    }

    private void checkInFlow() {
        int id = readInt("Enter booking id", 1, Integer.MAX_VALUE);
        Booking b = bookings.get(id);
        if (b == null) { System.out.println("No booking found"); return; }
        Room r = rooms.get(b.roomNumber);
        if (b.status != BookingStatus.RESERVED) {
            System.out.println("Only reserved bookings can be checked in");
            return;
        }
        if (r.status != RoomStatus.VACANT) {
            System.out.println("Room is not vacant");
            return;
        }
        b.status = BookingStatus.IN_HOUSE;
        r.status = RoomStatus.OCCUPIED;
        System.out.println("Checked in. Enjoy your stay");
    }

    private void checkOutFlow() {
        int id = readInt("Enter booking id", 1, Integer.MAX_VALUE);
        Booking b = bookings.get(id);
        if (b == null) { System.out.println("No booking found"); return; }
        Room r = rooms.get(b.roomNumber);
        if (b.status != BookingStatus.IN_HOUSE) {
            System.out.println("Only in house bookings can be checked out");
            return;
        }
        b.status = BookingStatus.COMPLETED;
        r.status = RoomStatus.VACANT;
        System.out.println("Checked out. Bill summary");
        printBill(b);
    }

    // -------------- Helpers --------------
    private void printBooking(Booking b) {
        System.out.println("Booking Details");
        System.out.printf(Locale.US,
                "Id %d Room %d Guest %s Status %s%nCheck in %s Check out %s Nights %d%nNightly rate %s Room charge %s Tax %s Total %s%n",
                b.id, b.roomNumber, b.guestName, b.status,
                b.checkIn.format(DTF), b.checkOut.format(DTF), b.nights,
                money(b.nightlyRate), money(b.roomCharge), money(b.tax), money(b.total));
    }

    private void printBill(Booking b) {
        System.out.println("------------------------------");
        System.out.printf("Room %d  %s%n", b.roomNumber, b.guestName);
        System.out.printf("Stay %s to %s  Nights %d%n",
                b.checkIn.format(DTF), b.checkOut.format(DTF), b.nights);
        System.out.printf("Nightly rate     %12s%n", money(b.nightlyRate));
        System.out.printf("Room charge      %12s%n", money(b.roomCharge));
        System.out.printf("Tax 10 percent   %12s%n", money(b.tax));
        System.out.println("------------------------------");
        System.out.printf("Total            %12s%n", money(b.total));
        System.out.println("------------------------------");
    }

    private BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal v) {
        return "₹" + round2(v).toPlainString();
    }

    private boolean validateDates(LocalDate in, LocalDate out) {
        if (!out.isAfter(in)) {
            System.out.println("Check out must be after check in");
            return false;
        }
        return true;
    }

    private List<Room> findAvailableRooms(RoomType type, LocalDate in, LocalDate out) {
        List<Room> candidates = new ArrayList<>();
        for (Room r : rooms.values()) {
            if (r.type == type && r.status != RoomStatus.OUT_OF_SERVICE) {
                candidates.add(r);
            }
        }
        List<Room> free = new ArrayList<>();
        outer:
        for (Room r : candidates) {
            for (Booking b : bookings.values()) {
                if (b.roomNumber == r.number && b.status != BookingStatus.CANCELLED && b.status != BookingStatus.COMPLETED) {
                    if (rangesOverlap(in, out, b.checkIn, b.checkOut)) {
                        continue outer;
                    }
                }
            }
            free.add(r);
        }
        return free;
    }

    private boolean rangesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    // -------------- Input readers --------------
    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt + " ");
            String s = sc.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v < min || v > max) {
                    System.out.println("Enter a number between " + min + " and " + max);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid integer");
            }
        }
    }

    private int readInt(String prompt, List<Integer> allowed) {
        Set<Integer> set = new HashSet<>(allowed);
        while (true) {
            System.out.print(prompt + " ");
            String s = sc.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (!set.contains(v)) {
                    System.out.println("Pick one of " + allowed);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid integer");
            }
        }
    }

    private String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt + " ");
            String s = sc.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("Value cannot be empty");
        }
    }

    private LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt + " ");
            String s = sc.nextLine().trim();
            try {
                return LocalDate.parse(s, DTF);
            } catch (DateTimeParseException e) {
                System.out.println("Enter date in format yyyy mm dd like 2025-11-02");
            }
        }
    }

    private RoomType pickRoomType() {
        System.out.println("Choose room type");
        RoomType[] types = RoomType.values();
        for (int i = 0; i < types.length; i++) {
            System.out.printf("%d %s Rate %s%n", i + 1, types[i], money(types[i].nightlyRate()));
        }
        int idx = readInt("Option", 1, types.length);
        return types[idx - 1];
    }
}
