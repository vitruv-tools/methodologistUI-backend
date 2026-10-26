package tools.vitruv.methodologist.vsum.helper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Private support methods that the generated utility class contains when its helpers need them.
 * Type references are written as {@code ${fully.qualified.Name}} and resolved through the class's
 * imports.
 */
final class HelperRuntime {

  static final String FIRST = "oclFirst";
  static final String LAST = "oclLast";
  static final String REVERSE = "oclReverse";
  static final String IS_UNIQUE = "oclIsUnique";
  static final String IS_TYPE_OF = "oclIsTypeOf";
  static final String CLOSURE = "oclClosure";
  static final String ITERATE = "oclIterate";
  static final String LET = "oclLet";

  private static final Pattern TYPE_REFERENCE = Pattern.compile("\\$\\{([\\w.]+)}");

  /** The methods in the order they appear in the generated class. */
  private static final Map<String, String> METHODS = new LinkedHashMap<>();

  static {
    METHODS.put(
        CLOSURE,
        """
        /**
         * The source elements and everything reachable from them via {@code step}, in
         * breadth-first order and without duplicates. Terminates on cyclic structures.
         */
        private static <T> ${java.util.List}<T> oclClosure(
            ${java.util.Collection}<? extends T> source,
            ${java.util.function.Function}<? super T, \
        ? extends ${java.util.Collection}<? extends T>> step) {
          ${java.util.Set}<T> result = new ${java.util.LinkedHashSet}<>();
          ${java.util.Deque}<T> pending = new ${java.util.ArrayDeque}<>();
          for (T element : source) {
            if (element != null && result.add(element)) {
              pending.add(element);
            }
          }
          while (!pending.isEmpty()) {
            for (T next : step.apply(pending.poll())) {
              if (next != null && result.add(next)) {
                pending.add(next);
              }
            }
          }
          return new ${java.util.ArrayList}<>(result);
        }
        """);
    METHODS.put(
        ITERATE,
        """
        /** Folds {@code source} into an accumulator, like OCL's {@code iterate}. */
        private static <T, A> A oclIterate(
            ${java.util.Collection}<? extends T> source,
            A initial,
            ${java.util.function.BiFunction}<? super T, ? super A, ? extends A> body) {
          A accumulator = initial;
          for (T element : source) {
            accumulator = body.apply(element, accumulator);
          }
          return accumulator;
        }
        """);
    METHODS.put(
        LET,
        """
        /** Evaluates {@code body} with {@code value} bound to its parameter, like OCL's let. */
        private static <V, R> R oclLet(
            V value, ${java.util.function.Function}<? super V, ? extends R> body) {
          return body.apply(value);
        }
        """);
    METHODS.put(
        IS_UNIQUE,
        """
        /** Whether {@code key} yields a different value for every element of {@code source}. */
        private static <T, K> boolean oclIsUnique(
            ${java.util.Collection}<T> source, ${java.util.function.Function}<? super T, K> key) {
          ${java.util.Set}<K> seen = new ${java.util.HashSet}<>();
          for (T element : source) {
            if (!seen.add(key.apply(element))) {
              return false;
            }
          }
          return true;
        }
        """);
    METHODS.put(
        FIRST,
        """
        /** The first element, or {@code null} if {@code source} is empty. */
        private static <T> T oclFirst(${java.util.Collection}<T> source) {
          ${java.util.Iterator}<T> iterator = source.iterator();
          return iterator.hasNext() ? iterator.next() : null;
        }
        """);
    METHODS.put(
        LAST,
        """
        /** The last element, or {@code null} if {@code source} is empty. */
        private static <T> T oclLast(${java.util.Collection}<T> source) {
          T last = null;
          for (T element : source) {
            last = element;
          }
          return last;
        }
        """);
    METHODS.put(
        REVERSE,
        """
        /** A copy of {@code source} in reverse order. */
        private static <T> ${java.util.List}<T> oclReverse(${java.util.Collection}<T> source) {
          ${java.util.List}<T> result = new ${java.util.ArrayList}<>(source);
          ${java.util.Collections}.reverse(result);
          return result;
        }
        """);
    METHODS.put(
        IS_TYPE_OF,
        """
        /** Whether {@code element} is a direct instance of the given EClass, like oclIsTypeOf. */
        private static boolean oclIsTypeOf(Object element, String nsUri, String className) {
          return element instanceof ${org.eclipse.emf.ecore.EObject} eObject
              && eObject.eClass().getName().equals(className)
              && nsUri.equals(eObject.eClass().getEPackage().getNsURI());
        }
        """);
  }

  private HelperRuntime() {}

  /** The source of the requested methods, in a stable order, with resolved type references. */
  static String render(Set<String> used, JavaImports imports) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> method : METHODS.entrySet()) {
      if (!used.contains(method.getKey())) {
        continue;
      }
      Matcher matcher = TYPE_REFERENCE.matcher(method.getValue());
      StringBuilder resolved = new StringBuilder();
      while (matcher.find()) {
        matcher.appendReplacement(
            resolved, Matcher.quoteReplacement(imports.ref(matcher.group(1))));
      }
      matcher.appendTail(resolved);
      builder.append('\n');
      resolved
          .toString()
          .lines()
          .forEach(line -> builder.append(line.isEmpty() ? "" : "  " + line).append('\n'));
    }
    return builder.toString();
  }
}
