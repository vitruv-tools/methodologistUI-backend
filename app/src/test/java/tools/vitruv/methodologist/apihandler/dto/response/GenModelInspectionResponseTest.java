package tools.vitruv.methodologist.apihandler.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenModelInspectionResponseTest {

  @Test
  void javaSerializationRoundTrip_retainsData() throws IOException, ClassNotFoundException {
    // Shape Jackson produces for an untyped JSON array of objects: a list of maps
    LinkedHashMap<String, String> entry = new LinkedHashMap<>();
    entry.put("type", "RENAME");
    entry.put("path", "model.genmodel");
    List<Serializable> data = new ArrayList<>();
    data.add(entry);

    GenModelInspectionResponse original =
        GenModelInspectionResponse.builder()
            .message("GenModel inspected successfully")
            .data(data)
            .build();

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(original);
    }

    GenModelInspectionResponse restored;
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (GenModelInspectionResponse) in.readObject();
    }

    assertThat(restored).isEqualTo(original);
    assertThat(restored.getData()).containsExactly(entry);
  }
}
