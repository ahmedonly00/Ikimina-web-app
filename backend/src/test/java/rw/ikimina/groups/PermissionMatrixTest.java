package rw.ikimina.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The permission matrix in code equals spec 5.5 ({@link SpecPermissionMatrix}), cell by cell. */
class PermissionMatrixTest {

    @Test
    void codeMatchesTheSpecificationCellByCell() {
        List<String> differences = new ArrayList<>();
        for (String[] row : SpecPermissionMatrix.TABLE) {
            Permission permission = Permission.valueOf(row[0]);
            for (GroupRole role : SpecPermissionMatrix.COLUMNS) {
                boolean spec = SpecPermissionMatrix.allows(role, row[0]);
                if (PermissionMatrix.allows(role, permission) != spec) {
                    differences.add(permission + " for " + role + ": spec says " + spec);
                }
            }
        }
        assertThat(differences).isEmpty();
    }

    @Test
    void everyPermissionInCodeIsInTheSpecTable() {
        assertThat(SpecPermissionMatrix.TABLE.length).isEqualTo(Permission.values().length);
    }
}
