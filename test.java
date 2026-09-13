public class Java17Example {
    public static void main(String[] args) {
        // "var" (desde Java 10) infiere el tipo automáticamente
        var s1 = new Circle(5);
        var s2 = new Square(4);

        s1.describe();
        s2.describe();

        // Diamond operator simplificado, funciona igual pero con "var"
        var shapes = new ArrayList<Shape>();
        shapes.add(s1);
        shapes.add(s2);

        for (var s : shapes) {
            System.out.println(s.area());
        }

        double total = shapes.stream()
                .mapToDouble(Shape::area)
                .sum();
        System.out.println("Total: " + total);

        // Switch expression (Java 14+) usando polimorfismo por tipo
        for (var s : shapes) {
            String msg = switch (s.getType()) {
                case "Circular" -> "Es un círculo";
                case "Cuadrada" -> "Es un cuadrado";
                default -> "Desconocido";
            };
            System.out.println(msg);
        }

        // Records (Java 16+): clase inmutable en una línea, sin boilerplate
        record Point(double x, double y) {}
        var p = new Point(3, 4);
        System.out.println(p.x() + ", " + p.y()); // getters automáticos
    }
}