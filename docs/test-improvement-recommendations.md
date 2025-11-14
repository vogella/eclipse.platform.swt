# Eclipse SWT Test Suite Improvement Recommendations

**Document Version:** 1.0
**Date:** 2025-11-14
**Analysis Scope:** Complete test suite analysis across all platform modules

## Executive Summary

This document provides comprehensive recommendations for improving the Eclipse SWT test suite based on a thorough analysis of the existing test infrastructure. The test suite currently contains 619 test files with approximately 41,378 lines of test code, 1,691 test methods, and 443 manual regression tests.

**Key Findings:**
- Strong foundation with JUnit 5, resource leak detection, and screenshot capture on failure
- Significant platform imbalance: GTK (30,047 lines) >> Win32 (3,495 lines) >> Cocoa (1,021 lines)
- 36+ tests tagged for GTK4 migration work still pending
- Browser tests separated due to fragility issues
- 443 manual tests not integrated into automated CI/CD pipeline

**Priority Recommendations:**
1. Address platform test coverage imbalance (especially Cocoa)
2. Migrate or fix 36+ GTK4-tagged tests
3. Automate manual regression tests
4. Stabilize browser test suite
5. Resolve 9+ disabled tests with long-standing issues

---

## Table of Contents

1. [Test Coverage Analysis](#1-test-coverage-analysis)
2. [Code Quality Improvements](#2-code-quality-improvements)
3. [Test Organization & Structure](#3-test-organization--structure)
4. [Platform-Specific Improvements](#4-platform-specific-improvements)
5. [Modern Testing Practices](#5-modern-testing-practices)
6. [Documentation & Maintainability](#6-documentation--maintainability)
7. [CI/CD Integration](#7-cicd-integration)
8. [Performance & Resource Management](#8-performance--resource-management)
9. [Implementation Roadmap](#9-implementation-roadmap)
10. [Appendix: Current Test Statistics](#10-appendix-current-test-statistics)

---

## 1. Test Coverage Analysis

### 1.1 Coverage Gaps Identified

#### Critical Gaps

**Browser Widget (High Priority)**
- **Issue:** Browser tests separated into `AllBrowserTests.java` due to fragility
- **Impact:** Core functionality not reliably tested in CI
- **Root Cause:** `Display.post(event)` unreliable, focus issues in automated builds
- **Recommendation:**
  ```java
  // Current fragile pattern:
  Display.getDefault().post(event); // Unreliable in CI

  // Proposed improvement:
  - Use WebDriver/Selenium for browser interaction testing
  - Implement retry logic with exponential backoff
  - Add @Tag("browser-manual") for tests requiring real user interaction
  - Create mock browser backend for unit tests
  ```
- **Files:** `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/Test_org_eclipse_swt_browser_Browser.java`

**Tracker Widget**
- **Issue:** Minimal test coverage despite being public API
- **Impact:** Regressions may go undetected
- **Recommendation:** Create comprehensive test class `Test_org_eclipse_swt_widgets_Tracker.java`
- **Priority:** Medium

**Accessibility Features**
- **Current Coverage:** Only basic event tests exist
- **Gap:** No comprehensive accessibility tree navigation, screen reader integration tests
- **Recommendation:**
  - Add platform-specific accessibility validation (MSAA on Windows, AT-SPI on Linux, NSAccessibility on macOS)
  - Test keyboard navigation compliance (Tab order, mnemonics)
  - Validate ARIA-equivalent properties

**OLE/COM (Windows)**
- **Issue:** No test coverage found
- **Impact:** Windows-specific embedding features untested
- **Recommendation:** Create `tests/org.eclipse.swt.tests.win32/JUnit Tests/org/eclipse/swt/tests/win32/Test_org_eclipse_swt_ole_*.java`

#### Secondary Gaps

**Custom Widgets**
- CoolItem test excluded from suite (marked "Failing test" in `AllWidgetTests.java:66`)
- ExpandBar, ExpandItem coverage appears limited

**Advanced Graphics**
- Transform operations
- Advanced path operations
- Print job rendering validation

**Platform Integration**
- Program associations (limited testing)
- Task bar integration
- System tray on all platforms

### 1.2 Platform Coverage Imbalance

| Platform | Lines of Test Code | Percentage | Recommended Target |
|----------|-------------------|------------|-------------------|
| GTK (Linux) | 30,047 | 73% | 40-45% |
| Win32 (Windows) | 3,495 | 8% | 30-35% |
| Cocoa (macOS) | 1,021 | 2% | 20-25% |
| Cross-platform | 6,815 | 17% | 15-20% |

**Actionable Steps:**
1. **Immediate:** Bring Cocoa up to at least 5,000 lines (5x current)
2. **Short-term:** Increase Win32 to 10,000+ lines
3. **Long-term:** Balance to roughly match market share of desktop OSes

**Specific Cocoa Gaps:**
- Only 1,021 lines vs 30,047 for GTK
- Many tests disabled on Mac with "Bug 536564" or similar
- Minimal Cocoa-specific API testing (NSView, NSWindow integration)

---

## 2. Code Quality Improvements

### 2.1 Resolve Disabled Tests

**High-Priority Disabled Tests:**

```java
// File: Test_org_eclipse_swt_events_KeyEvent.java (Win32)
// Issue: "Have been broken for ages, maybe not worth fixing"
@Disabled("Have been broken for ages, maybe not worth fixing")
void test_sendKeyEvent_numpadKeys() { ... }
```

**Recommendation:**
- Either fix within next release cycle OR
- Document why test is invalid and remove
- DO NOT leave disabled tests indefinitely

**Current Disabled Test Inventory:**
- 9 tests explicitly disabled with `@Disabled` annotation
- 36+ tests tagged `@Tag("gtk4-todo")` or `@Tag("gtk4-wayland-todo")`
- Multiple tests using `assumeTrue()` to skip on certain platforms

**Action Plan:**
1. Create GitHub issues for each disabled test
2. Assign priority/severity labels
3. Set target milestone for resolution (or removal)
4. Track progress in project board

### 2.2 Address TODO Comments

**Found 50+ TODO/FIXME comments across test suite:**

```java
// tests/org.eclipse.swt.tests/JUnit Tests/.../Test_org_eclipse_swt_widgets_Composite.java:127
//TODO Fix Cocoa failure.
```

**Recommendation:**
- Convert all TODO comments to tracked GitHub issues
- Link test code to issue: `// See issue #12345`
- Remove stale TODOs (>2 years old) or convert to permanent documentation

**Pattern to Adopt:**
```java
// BEFORE:
//TODO Fix GTK failure.

// AFTER:
// KNOWN ISSUE: Test fails on GTK due to timing issue with async event dispatch
// See: https://github.com/eclipse-platform/eclipse.platform.swt/issues/12345
// Workaround: Use @Tag("manual") until resolved
```

### 2.3 Improve Assertion Quality

**Current Pattern (Good):**
```java
assertEquals(expected, actual); // ✓ Good
assertTrue(condition);          // ✓ Good
```

**Missing Custom Messages:**
```java
// BEFORE (low value error message):
assertEquals(50, height);

// AFTER (actionable error message):
assertEquals(50, height,
  "CTabFolder height should be at least 50, got " + height +
  ". This may indicate Bug 507611 regression.");
```

**Recommendation:**
- Add descriptive messages to all assertions in critical tests
- Include context: expected behavior, platform, related bug numbers
- Use `assertAll()` for related assertions to see all failures at once

**Example Enhancement:**
```java
@Test
void test_computeSize_Composite() {
    Composite composite = new Composite(shell, SWT.NONE);
    Point size = composite.computeSize(SWT.DEFAULT, SWT.DEFAULT);

    // ENHANCED with assertAll and messages:
    assertAll("Composite size validation",
        () -> assertTrue(size.x >= 0,
            "Width must be non-negative, got: " + size.x),
        () -> assertTrue(size.y >= 0,
            "Height must be non-negative, got: " + size.y),
        () -> assertNotEquals(new Point(0, 0), size,
            "Composite with default size should have non-zero dimensions")
    );
}
```

### 2.4 Reduce Test Code Duplication

**Pattern Identified:**
Many tests repeat similar setup/assertion patterns:

```java
// Repeated in multiple test classes:
@Test
void test_setSelection_NullArray() {
    assertThrows(IllegalArgumentException.class,
        () -> widget.setSelection((int[]) null));
}
```

**Recommendation:**
Create shared test utilities for common patterns:

```java
// New class: tests/org.eclipse.swt.tests/JUnit Tests/.../AssertionUtil.java
public class AssertionUtil {
    public static void assertThrowsIllegalArgumentForNull(
        Consumer<?> methodCall, String paramName) {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> methodCall.accept(null)
        );
        assertTrue(ex.getMessage().contains(paramName),
            "Exception message should mention parameter '" + paramName + "'");
    }
}

// Usage in tests:
AssertionUtil.assertThrowsIllegalArgumentForNull(
    widget::setSelection, "selection");
```

---

## 3. Test Organization & Structure

### 3.1 Automate Manual Tests

**Current State:**
- 443 manual tests in `/ManualTests/` directories
- Naming pattern: `Bug<number>_<description>.java`
- Must be run manually, not included in CI

**Examples:**
```
tests/org.eclipse.swt.tests/ManualTests/org/eclipse/swt/tests/manual/
  Bug205199_Label_Image_vs_Text.java
  Bug548982_TreeAddRemoveMany.java
  Issue0932_StyledText_FixedLineHeight.java
```

**Recommendation:**

1. **Categorize Manual Tests:**
   - **Automatable:** Can be converted to JUnit tests with assertions
   - **Visual Verification:** Require screenshot comparison
   - **Interactive:** Truly require human interaction

2. **Automate Where Possible:**
   ```java
   // BEFORE (Manual test):
   public class Bug205199_Label_Image_vs_Text {
       public static void main(String[] args) {
           Display display = new Display();
           Shell shell = new Shell(display);
           // ... create widgets ...
           shell.open();
           while (!shell.isDisposed()) {
               if (!display.readAndDispatch()) display.sleep();
           }
       }
   }

   // AFTER (Automated test):
   @Test
   void test_Bug205199_Label_ImageAndText() {
       Label label = new Label(shell, SWT.NONE);
       Image image = new Image(display, 16, 16);
       label.setImage(image);
       label.setText("Text");

       assertNotNull(label.getImage(), "Image should be set");
       assertEquals("Text", label.getText(), "Text should be set");
       assertTrue(label.getSize().x > 16, "Label should accommodate both");

       image.dispose();
   }
   ```

3. **Visual Regression Testing:**
   - Use screenshot comparison library (e.g., Ashot, Shutterbug)
   - Store baseline images in `tests/resources/screenshots/baseline/`
   - Compare on each run, fail if diff exceeds threshold

4. **Migration Strategy:**
   - Target: Convert 50% of manual tests to automated within 6 months
   - Priority: Recent bugs (last 2 years) first
   - Tag remaining manual tests: `@Tag("requires-visual-inspection")`

### 3.2 Improve Test Suite Organization

**Current Suite Structure:**
```java
AllNonBrowserTests.java   // Everything except browser
AllBrowserTests.java      // Browser only (fragile)
AllGraphicsTests.java     // Graphics subset
AllWidgetTests.java       // Widgets subset
```

**Recommended Enhanced Structure:**
```
AllTests.java                    // Master suite
├── AllCoreTests.java           // SWT, Display, Shell
├── AllWidgetTests.java         // All widgets (existing)
├── AllGraphicsTests.java       // All graphics (existing)
├── AllEventTests.java          // All events (new)
├── AllLayoutTests.java         // All layouts (new)
├── AllAccessibilityTests.java  // Accessibility (new)
├── AllDndTests.java            // Drag & Drop (new)
├── AllBrowserTests.java        // Browser (existing, fix fragility)
└── AllPlatformTests.java       // Platform-specific
    ├── AllGTKTests.java
    ├── AllWin32Tests.java
    └── AllCocoaTests.java
```

**Benefits:**
- Easier to run subset of tests during development
- Better CI pipeline stages (fast tests first, slow tests later)
- Clearer failure reporting

### 3.3 Test Naming Conventions

**Current Convention (Good):**
```
Test_org_eclipse_swt_widgets_Button.java
Test_org_eclipse_swt_graphics_Image.java
```

**Enhancement Recommendations:**

1. **Use Nested Test Classes for Better Organization:**
   ```java
   class Test_org_eclipse_swt_widgets_Button {
       @Nested
       class ConstructorTests {
           @Test void test_Constructor_NullParent() { ... }
           @Test void test_Constructor_InvalidStyle() { ... }
       }

       @Nested
       class SelectionTests {
           @Test void test_addSelectionListener() { ... }
           @Test void test_removeSelectionListener() { ... }
       }

       @Nested
       class TextAndImageTests {
           @Test void test_setText() { ... }
           @Test void test_setImage() { ... }
       }
   }
   ```

2. **Use `@DisplayName` for Readability:**
   ```java
   @Test
   @DisplayName("Button should fire SelectionEvent when clicked")
   void test_selectionEventFired() { ... }
   ```

3. **Tag Tests by Category:**
   ```java
   @Tag("widget")
   @Tag("button")
   @Tag("selection")
   @Test
   void test_selectionEvent() { ... }
   ```

---

## 4. Platform-Specific Improvements

### 4.1 GTK-Specific Recommendations

**Current State:**
- 36+ tests tagged `@Tag("gtk4-todo")` or `@Tag("gtk4-wayland-todo")`
- Strong overall coverage (30,047 lines)
- GTK3/GTK4 transition ongoing

**Recommendations:**

1. **GTK4 Migration Dashboard:**
   - Create tracking issue for all 36+ GTK4 todos
   - Break down into milestones (e.g., "GTK4 Widgets Complete", "GTK4 Graphics Complete")
   - Assign owners to each subsystem

2. **Wayland-Specific Testing:**
   ```java
   // Current pattern (good):
   @Tag("gtk4-wayland-todo")
   @Test
   void test_clipboard() {
       assumeTrue(SwtTestUtil.isX11(), "Test requires X11");
       // ... test code ...
   }

   // Enhanced pattern:
   @ParameterizedTest
   @EnumSource(WindowSystem.class) // X11, Wayland
   void test_clipboard(WindowSystem ws) {
       // Test on both window systems
   }
   ```

3. **GTK Version Matrix Testing:**
   - Test on GTK 3.22 (minimum supported)
   - Test on GTK 3.24 (common)
   - Test on GTK 4.x (future)

### 4.2 Win32-Specific Recommendations

**Current State:**
- Only 3,495 lines (8% of total)
- Many KeyEvent tests disabled
- Some DPI-related tests exist (good)

**Recommendations:**

1. **Expand KeyEvent Testing:**
   - Investigate why KeyEvent tests "broken for ages"
   - Either fix or replace with alternative approach (e.g., SendInput API)
   - Add keyboard layout tests (US, UK, German, etc.)

2. **Windows Version Matrix:**
   - Test on Windows 10 (minimum supported)
   - Test on Windows 11
   - Test on Windows Server (if applicable)

3. **High-DPI Testing:**
   - Excellent start with `Win32DPIUtilTests.java` (30+ tests)
   - Expand to test all widgets at 100%, 125%, 150%, 200%, 300% scaling
   - Test mixed-DPI scenarios (multi-monitor)

4. **Windows-Specific APIs:**
   ```java
   // Add tests for:
   - TaskBar integration (jump lists, progress, overlays)
   - Windows notifications
   - Dark mode support (Windows 10+)
   - Acrylic/Mica effects (Windows 11)
   ```

### 4.3 Cocoa-Specific Recommendations (CRITICAL)

**Current State:**
- Only 1,021 lines (2% of total, severely lacking)
- Many tests disabled with "Bug 536564" or similar
- Minimal platform-specific testing

**Recommendations (High Priority):**

1. **Immediate Actions:**
   - **Target:** Increase to 5,000+ lines within 3 months
   - **Focus Areas:**
     - Retina display support
     - macOS-specific widgets (e.g., SearchField behavior)
     - Menu bar integration (global menu)
     - Notification center integration

2. **macOS Version Matrix:**
   - Test on macOS 11 Big Sur (minimum supported)
   - Test on macOS 12 Monterey
   - Test on macOS 13 Ventura
   - Test on macOS 14 Sonoma (latest)

3. **Apple Silicon Testing:**
   - Ensure all tests run on both Intel and Apple Silicon
   - Test Rosetta 2 compatibility if needed

4. **Fix Disabled Tests:**
   ```java
   // Current pattern:
   @DisabledOnOs(value=OS.MAC, disabledReason = "Test fails on Mac: Bug 536564")

   // Action required:
   - Investigate Bug 536564
   - Either fix the test or fix the SWT code
   - Document why behavior differs on macOS if intentional
   ```

5. **macOS-Specific Features:**
   ```java
   // Add tests for:
   @Test
   void test_nativeMenuBar() { ... }           // Global menu bar

   @Test
   void test_touchBarSupport() { ... }         // Touch Bar (if applicable)

   @Test
   void test_retinaImageScaling() { ... }      // @2x image handling

   @Test
   void test_darkModeTransitions() { ... }     // Light/dark mode switching

   @Test
   void test_fullScreenTransitions() { ... }   // macOS fullscreen behavior
   ```

### 4.4 Cross-Platform Consistency Tests

**New Test Category:**

```java
/**
 * Tests that verify consistent behavior across all platforms
 */
public class CrossPlatformConsistencyTests {

    @ParameterizedTest
    @EnumSource(Platform.class) // GTK, Win32, Cocoa
    void test_buttonSize_consistentAcrossPlatforms(Platform platform) {
        // Run same test on all platforms
        // Assert behavior is consistent (or document differences)
    }

    @Test
    void test_colorConstants_identicalAcrossPlatforms() {
        // SWT.COLOR_* should map to same RGB values
        // (or document intentional differences)
    }
}
```

---

## 5. Modern Testing Practices

### 5.1 Parameterized Tests

**Current Pattern:**
```java
// Repeated test methods:
@Test void test_setSelection_Index0() { ... }
@Test void test_setSelection_Index1() { ... }
@Test void test_setSelection_IndexNegative() { ... }
```

**Recommended Pattern:**
```java
@ParameterizedTest
@ValueSource(ints = {0, 1, 5, 10})
void test_setSelection_ValidIndices(int index) {
    table.setSelection(index);
    assertEquals(index, table.getSelectionIndex());
}

@ParameterizedTest
@ValueSource(ints = {-1, -100, 1000})
void test_setSelection_InvalidIndices(int index) {
    table.setSelection(index);
    assertEquals(-1, table.getSelectionIndex(),
        "Invalid index should not change selection");
}
```

**More Complex Parameterization:**
```java
@ParameterizedTest
@CsvSource({
    "PUSH,    true,  false",
    "CHECK,   true,  true",
    "RADIO,   true,  true",
    "TOGGLE,  false, true"
})
void test_buttonStyles(String style, boolean supportsText, boolean supportsSelection) {
    int styleConstant = SWT.class.getField(style).getInt(null);
    Button button = new Button(shell, styleConstant);

    if (supportsText) {
        button.setText("Test");
        assertEquals("Test", button.getText());
    }

    if (supportsSelection) {
        button.setSelection(true);
        assertTrue(button.getSelection());
    }
}
```

### 5.2 Test Fixtures and Builders

**Problem: Complex Widget Setup Duplication**

**Solution: Builder Pattern for Test Fixtures**
```java
// New utility class:
public class WidgetBuilder {
    public static TableBuilder table(Composite parent) {
        return new TableBuilder(parent);
    }

    public static class TableBuilder {
        private Table table;
        private List<String[]> items = new ArrayList<>();

        TableBuilder(Composite parent) {
            table = new Table(parent, SWT.NONE);
        }

        public TableBuilder withColumns(String... headers) {
            for (String header : headers) {
                TableColumn col = new TableColumn(table, SWT.NONE);
                col.setText(header);
            }
            return this;
        }

        public TableBuilder withItem(String... values) {
            items.add(values);
            return this;
        }

        public Table build() {
            for (String[] values : items) {
                TableItem item = new TableItem(table, SWT.NONE);
                item.setText(values);
            }
            return table;
        }
    }
}

// Usage in tests:
@Test
void test_tableSelection() {
    Table table = table(shell)
        .withColumns("Name", "Age", "City")
        .withItem("Alice", "30", "NYC")
        .withItem("Bob", "25", "LA")
        .build();

    table.setSelection(0);
    assertEquals("Alice", table.getSelection()[0].getText(0));
}
```

### 5.3 Custom Assertions

**Create Domain-Specific Assertions:**

```java
public class SWTAssertions {
    public static void assertWidgetDisposed(Widget widget) {
        assertTrue(widget.isDisposed(),
            widget.getClass().getSimpleName() + " should be disposed");
    }

    public static void assertWidgetNotDisposed(Widget widget) {
        assertFalse(widget.isDisposed(),
            widget.getClass().getSimpleName() + " should not be disposed");
    }

    public static void assertColorEquals(RGB expected, Color actual) {
        RGB actualRGB = actual.getRGB();
        assertAll("RGB color comparison",
            () -> assertEquals(expected.red, actualRGB.red, "Red component"),
            () -> assertEquals(expected.green, actualRGB.green, "Green component"),
            () -> assertEquals(expected.blue, actualRGB.blue, "Blue component")
        );
    }

    public static void assertRectangleContains(Rectangle outer, Rectangle inner) {
        assertTrue(outer.contains(inner.x, inner.y),
            "Rectangle " + outer + " should contain point (" + inner.x + "," + inner.y + ")");
        assertTrue(outer.contains(inner.x + inner.width, inner.y + inner.height),
            "Rectangle " + outer + " should contain point (" +
            (inner.x + inner.width) + "," + (inner.y + inner.height) + ")");
    }
}

// Usage:
SWTAssertions.assertWidgetDisposed(button);
SWTAssertions.assertColorEquals(new RGB(255, 0, 0), redColor);
```

### 5.4 Test Data Management

**Current Issue:**
Test data (images, fonts, files) scattered or embedded in code

**Recommendation:**
```
tests/org.eclipse.swt.tests/resources/
├── images/
│   ├── baseline/           # Screenshot baselines
│   ├── test-images/        # Test input images
│   └── invalid/            # Corrupt images for error handling
├── fonts/
│   └── test-fonts/         # Custom fonts for testing
└── data/
    ├── test-data.csv       # Parameterized test data
    └── xml-configs/        # Test configurations
```

**Access Test Resources:**
```java
public class TestResources {
    private static final Path RESOURCES_DIR =
        Paths.get("tests/org.eclipse.swt.tests/resources");

    public static Image loadTestImage(String name) {
        Path imagePath = RESOURCES_DIR.resolve("images/test-images/" + name);
        return new Image(Display.getDefault(), imagePath.toString());
    }

    public static Path getBaselineScreenshot(String testName) {
        return RESOURCES_DIR.resolve("images/baseline/" + testName + ".png");
    }
}
```

### 5.5 Mocking and Stubbing

**Current Pattern:**
Real Display and Shell created for every test (expensive)

**Selective Mocking for Unit Tests:**
```java
// For true unit tests (testing logic, not UI):
public interface IDisplay {
    void asyncExec(Runnable runnable);
    void syncExec(Runnable runnable);
}

public class DisplayWrapper implements IDisplay {
    private Display display;
    // ... wrapper implementation ...
}

// In tests:
@Test
void test_asyncExecution_withMock() {
    IDisplay mockDisplay = mock(IDisplay.class);
    // Test logic without creating real Display
    verify(mockDisplay, times(1)).asyncExec(any(Runnable.class));
}
```

**Note:** Mocking should be limited - most SWT tests need real widgets. Use mocking for:
- Listener verification
- Event sequence testing
- Thread safety testing
- Performance testing (avoid heavy UI creation)

---

## 6. Documentation & Maintainability

### 6.1 Test Documentation

**Current State:**
- Good Javadoc on test classes: `/** Automated Test Suite for class ... */`
- Minimal method-level documentation

**Recommendations:**

1. **Document Test Intent:**
   ```java
   /**
    * Verifies that Button properly handles selection state for CHECK style.
    *
    * <p>Regression test for Bug 123456 where CHECK buttons would not
    * properly toggle selection state on programmatic setSelection() calls.</p>
    *
    * <p><b>Test Steps:</b></p>
    * <ol>
    *   <li>Create CHECK button</li>
    *   <li>Call setSelection(true)</li>
    *   <li>Verify getSelection() returns true</li>
    *   <li>Verify SelectionEvent fired</li>
    * </ol>
    *
    * @see <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=123456">Bug 123456</a>
    */
   @Test
   void test_setSelection_CheckButton() { ... }
   ```

2. **Document Platform-Specific Behavior:**
   ```java
   /**
    * Tests Shell modality behavior.
    *
    * <p><b>Platform Notes:</b></p>
    * <ul>
    *   <li><b>GTK:</b> Uses gtk_window_set_modal()</li>
    *   <li><b>Win32:</b> Uses WS_DISABLED on parent</li>
    *   <li><b>Cocoa:</b> Uses [NSWindow beginSheet:]</li>
    * </ul>
    *
    * <p>Behavior should be identical across platforms despite different
    * native implementations.</p>
    */
   @Test
   void test_shellModality() { ... }
   ```

3. **Create Test Plan Documents:**
   ```
   docs/testing/
   ├── test-plan-widgets.md      # Widget testing strategy
   ├── test-plan-graphics.md     # Graphics testing strategy
   ├── test-plan-accessibility.md
   └── platform-test-matrix.md   # Platform/version compatibility matrix
   ```

### 6.2 Test Maintenance Guidelines

**Create `docs/testing/TEST_MAINTENANCE.md`:**

```markdown
# SWT Test Maintenance Guidelines

## When to Update Tests

1. **API Changes:** Update tests within same commit
2. **Bug Fixes:** Add regression test before fix
3. **Platform Updates:** Review and update platform-specific tests
4. **Deprecations:** Update tests to use new APIs, keep old API tests until removal

## Test Review Checklist

Before merging test changes:
- [ ] All tests pass on all three platforms (GTK, Win32, Cocoa)
- [ ] Test names follow `test_<methodName>_<scenario>` convention
- [ ] Resource disposal verified (no leaks)
- [ ] Assertions have descriptive messages
- [ ] Platform-specific behavior documented
- [ ] TODO comments converted to GitHub issues

## Disabled Test Policy

- **New disabled tests:** Must have GitHub issue and target milestone
- **Existing disabled tests:** Review quarterly, fix or remove
- **Maximum disabled duration:** 6 months without progress = remove test
```

### 6.3 Test Code Reviews

**Recommendations:**

1. **Dedicated Test Review Guidelines:**
   - Require platform test runs before merge
   - Verify resource cleanup in all tests
   - Check for flaky tests (run 10x to verify stability)

2. **Automated Checks:**
   ```yaml
   # .github/workflows/test-quality.yml
   - name: Check for TODOs in new tests
     run: |
       if git diff origin/master tests/ | grep -i "TODO\|FIXME"; then
         echo "New tests contain TODO/FIXME - create issues instead"
         exit 1
       fi
   ```

---

## 7. CI/CD Integration

### 7.1 Test Execution Strategy

**Current Gaps:**
- Browser tests run separately (fragile)
- Manual tests not run in CI
- No clear fast/slow test separation

**Recommended CI Pipeline:**

```yaml
# .github/workflows/test-pipeline.yml

jobs:
  fast-tests:
    name: Fast Tests (Unit)
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
    steps:
      - name: Run unit tests
        run: mvn verify -pl tests/org.eclipse.swt.tests
        timeout-minutes: 10

  widget-tests:
    name: Widget Tests
    needs: fast-tests
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
    steps:
      - name: Run widget tests
        run: mvn verify -pl tests/org.eclipse.swt.tests -Dtest.suite=AllWidgetTests
        timeout-minutes: 20

  graphics-tests:
    name: Graphics Tests
    needs: fast-tests
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
    steps:
      - name: Run graphics tests
        run: mvn verify -pl tests/org.eclipse.swt.tests -Dtest.suite=AllGraphicsTests
        timeout-minutes: 20

  browser-tests:
    name: Browser Tests (Nightly)
    # Run only on schedule, not on every commit
    if: github.event_name == 'schedule'
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
    steps:
      - name: Run browser tests
        run: mvn verify -pl tests/org.eclipse.swt.tests -Dtest.suite=AllBrowserTests
        continue-on-error: true  # Don't fail build on browser test failures
        timeout-minutes: 30

  platform-specific-tests:
    name: Platform Tests - ${{ matrix.platform }}
    needs: fast-tests
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        include:
          - platform: GTK
            os: ubuntu-latest
            module: tests/org.eclipse.swt.tests.gtk
          - platform: Win32
            os: windows-latest
            module: tests/org.eclipse.swt.tests.win32
          - platform: Cocoa
            os: macos-latest
            module: tests/org.eclipse.swt.tests.cocoa
    steps:
      - name: Run platform tests
        run: mvn verify -pl ${{ matrix.module }}
        timeout-minutes: 15
```

### 7.2 Test Reporting

**Recommendations:**

1. **Unified Test Reports:**
   ```yaml
   - name: Publish Test Results
     uses: EnricoMi/publish-unit-test-result-action@v2
     if: always()
     with:
       files: '**/target/surefire-reports/*.xml'
       check_name: Test Results - ${{ matrix.platform }}
   ```

2. **Flaky Test Detection:**
   ```yaml
   - name: Detect Flaky Tests
     uses: marketplace/actions/flaky-test-detector
     with:
       run-count: 5
       failure-threshold: 20%  # Flag if fails >20% of runs
   ```

3. **Coverage Reporting:**
   ```yaml
   - name: Generate Coverage Report
     run: mvn jacoco:report

   - name: Upload Coverage to Codecov
     uses: codecov/codecov-action@v3
     with:
       files: target/site/jacoco/jacoco.xml
       flags: ${{ matrix.platform }}
   ```

### 7.3 Test Isolation

**Current Issue:**
Tests may interfere with each other (shared Display, resource leaks)

**Recommendations:**

1. **JUnit 5 Parallel Execution:**
   ```java
   // junit-platform.properties
   junit.jupiter.execution.parallel.enabled = true
   junit.jupiter.execution.parallel.mode.default = same_thread
   junit.jupiter.execution.parallel.mode.classes.default = concurrent

   // Limit parallelism for UI tests (not thread-safe):
   junit.jupiter.execution.parallel.config.strategy = fixed
   junit.jupiter.execution.parallel.config.fixed.parallelism = 1
   ```

2. **Test Execution Order:**
   ```java
   // Ensure Display tests run first:
   @Suite
   @SelectClasses({
       Test_org_eclipse_swt_widgets_Display.class,  // MUST be first
       // ... other tests ...
   })
   ```

3. **Resource Cleanup Verification:**
   - Already implemented well with `AllNonBrowserTests.java` resource leak detection
   - Extend to all test suites

---

## 8. Performance & Resource Management

### 8.1 Test Execution Performance

**Current State:**
- Memory leak tests use 50,000+ iterations (slow but thorough)
- No clear performance baseline

**Recommendations:**

1. **Performance Budgets:**
   ```java
   @Test
   @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
   void test_buttonCreation_performance() {
       // Test must complete within 100ms
       for (int i = 0; i < 100; i++) {
           Button button = new Button(shell, SWT.PUSH);
           button.dispose();
       }
   }
   ```

2. **Separate Performance Tests:**
   ```java
   @Tag("performance")
   @Test
   void test_largeTablePerformance() {
       // Only run when explicitly requested
       long startTime = System.nanoTime();

       Table table = new Table(shell, SWT.VIRTUAL);
       table.setItemCount(1_000_000);

       long duration = System.nanoTime() - startTime;
       assertTrue(duration < 1_000_000_000, // < 1 second
           "Creating 1M virtual table items took " + duration + "ns");
   }
   ```

3. **Benchmark Tracking:**
   - Store performance results over time
   - Alert on regression (>10% slower than baseline)

### 8.2 Resource Leak Detection Enhancement

**Current Implementation (Good):**
```java
// AllNonBrowserTests.java
Resource.setNonDisposeHandler (error -> {
    leakedResources.add (error);
});
```

**Enhancements:**

1. **Leak Source Tracking:**
   ```java
   @AfterEach
   void verifyNoLeaksInTest() {
       int leaksBefore = getLeakCount();
       // ... test execution ...
       int leaksAfter = getLeakCount();

       if (leaksAfter > leaksBefore) {
           fail("Test leaked " + (leaksAfter - leaksBefore) + " resources");
       }
   }
   ```

2. **Leak Category Reporting:**
   ```java
   Map<Class<?>, Integer> leaksByType = new HashMap<>();
   Resource.setNonDisposeHandler (error -> {
       // Track which resource types leak most
       Class<?> resourceType = extractResourceType(error);
       leaksByType.merge(resourceType, 1, Integer::sum);
   });

   @AfterSuite
   void reportLeakStatistics() {
       System.out.println("Leak Summary:");
       leaksByType.forEach((type, count) ->
           System.out.println("  " + type.getSimpleName() + ": " + count));
   }
   ```

### 8.3 Test Data Size Management

**Recommendation:**
- Limit test image sizes (max 100KB per image)
- Use SVG where possible (smaller, scalable)
- Compress baseline screenshots
- Clean up old baseline images when tests removed

---

## 9. Implementation Roadmap

### Phase 1: Critical Issues (0-3 months)

**Priority 1: Platform Balance**
- [ ] Increase Cocoa test coverage to 5,000+ lines
  - Owner: Cocoa maintainer
  - Deliverable: 50+ new Cocoa-specific tests
  - Timeline: 3 months

- [ ] Resolve all disabled tests
  - Owner: Test lead
  - Deliverable: 0 tests with `@Disabled` annotation
  - Timeline: 2 months

**Priority 2: GTK4 Migration**
- [ ] Complete 36+ GTK4 tagged tests
  - Owner: GTK maintainer
  - Deliverable: Remove all `@Tag("gtk4-todo")` tags
  - Timeline: 3 months

**Priority 3: Browser Test Stabilization**
- [ ] Fix browser test fragility
  - Owner: Browser component owner
  - Deliverable: Browser tests run reliably in CI
  - Timeline: 2 months

### Phase 2: Quality Improvements (3-6 months)

**Documentation**
- [ ] Create test maintenance guidelines
- [ ] Document platform-specific behavior in tests
- [ ] Convert all TODO comments to GitHub issues

**Test Organization**
- [ ] Implement enhanced test suite structure
- [ ] Migrate 50% of manual tests to automated
- [ ] Create cross-platform consistency test suite

**Modern Practices**
- [ ] Introduce parameterized tests (20+ tests converted)
- [ ] Create custom assertion library
- [ ] Implement test data management structure

### Phase 3: Advanced Features (6-12 months)

**CI/CD**
- [ ] Implement multi-stage test pipeline
- [ ] Add flaky test detection
- [ ] Set up coverage tracking with trend reports

**Performance**
- [ ] Create performance test suite with baselines
- [ ] Implement performance regression detection
- [ ] Optimize slow tests (target: 50% faster test suite)

**Coverage Expansion**
- [ ] Add comprehensive accessibility tests
- [ ] Create OLE/COM test suite (Win32)
- [ ] Expand Tracker widget tests
- [ ] Add visual regression testing

### Metrics & Success Criteria

**Test Coverage:**
- Target: 80% line coverage, 70% branch coverage
- Current baseline: Measure and establish

**Platform Balance:**
- GTK: 40-45% (currently 73%)
- Win32: 30-35% (currently 8%)
- Cocoa: 20-25% (currently 2%)

**Test Stability:**
- Flaky test rate: <1% (currently unknown)
- Disabled tests: 0 (currently 9+)
- GTK4 todos: 0 (currently 36+)

**Execution Performance:**
- Full test suite: <30 minutes (all platforms)
- Fast test subset: <5 minutes
- Manual test automation: 50% converted

---

## 10. Appendix: Current Test Statistics

### 10.1 Test Module Summary

| Module | Test Files | Lines of Code | Test Methods (@Test) | Manual Tests |
|--------|-----------|---------------|---------------------|--------------|
| org.eclipse.swt.tests | 142 | 6,815 | ~500 | 443 |
| org.eclipse.swt.tests.gtk | 1 + inherited | 30,047 | ~800 | ~200 |
| org.eclipse.swt.tests.win32 | Few + inherited | 3,495 | ~250 | ~150 |
| org.eclipse.swt.tests.cocoa | 0 + inherited | 1,021 | ~140 | ~50 |
| **Total** | **619** | **41,378** | **1,691** | **443** |

### 10.2 Test Coverage by Component

| Component | Test Classes | Coverage Assessment |
|-----------|-------------|-------------------|
| Widgets | 47 | ✓ Good |
| Graphics | 20 | ✓ Good |
| Events | 14 | ✓ Good |
| Layouts | 3 | ⚠ Moderate |
| DND | 8 | ✓ Good |
| Accessibility | 4 | ⚠ Basic only |
| Browser | 1 | ✗ Fragile |
| Printing | 3 | ⚠ Moderate |
| Custom Widgets | 17 | ✓ Good |
| OLE/COM | 0 | ✗ None |

### 10.3 Disabled/Problematic Tests

| Category | Count | Status |
|----------|-------|--------|
| @Disabled tests | 9 | Needs resolution |
| @Tag("gtk4-todo") | 25 | In progress |
| @Tag("gtk4-wayland-todo") | 11 | In progress |
| Browser tests separated | ~50 | Fragile, needs fixing |
| assumeTrue() skips | 77 | Platform-specific, acceptable |
| TODO comments | 50+ | Should convert to issues |

### 10.4 Test Execution Times (Estimated)

| Test Suite | Estimated Duration | Run Frequency |
|------------|------------------|---------------|
| AllNonBrowserTests | ~15-20 minutes | Every commit |
| AllBrowserTests | ~10-15 minutes | Nightly only |
| Platform-specific (each) | ~5-10 minutes | Every commit |
| Performance tests | ~30-60 minutes | Weekly |
| Manual tests | N/A | On demand |

---

## Conclusion

The Eclipse SWT test suite has a strong foundation with comprehensive widget coverage, resource leak detection, and good use of modern JUnit 5 features. However, significant improvements are needed in:

1. **Platform balance** - Cocoa severely underrepresented
2. **Test stability** - Browser tests fragile, 9+ disabled tests
3. **GTK4 migration** - 36+ tests awaiting completion
4. **Manual test automation** - 443 tests not in CI pipeline

By following the recommendations in this document, the SWT project can achieve:
- More reliable CI/CD pipeline
- Better cross-platform consistency validation
- Faster feedback cycles for developers
- Higher confidence in releases

**Next Steps:**
1. Review and prioritize recommendations
2. Assign owners to Phase 1 items
3. Create GitHub issues for tracking
4. Establish baseline metrics
5. Begin implementation

---

**Document Maintenance:**
- **Owner:** SWT Test Team
- **Review Frequency:** Quarterly
- **Last Updated:** 2025-11-14
- **Next Review:** 2025-02-14
