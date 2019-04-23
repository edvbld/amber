/**
 * @test /nodynamiccopyright/
 * @bug 8214031
 * @summary Verify that definite assignment when true works (illegal code)
 * @compile/fail/ref=DefiniteAssignment2.out --enable-preview --source ${jdk.version} -XDrawDiagnostics DefiniteAssignment2.java
 */
public class DefiniteAssignment2 {

    public static void main(String[] args) {
        int a = 0;
        boolean b = true;
        boolean t;

        {
            int x;

            t = (b && switch(a) {
                case 0: break-with (x = 1) == 1 || true;
                default: break-with false;
            }) || x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break-with (x = 1) == 1;
                default: break-with false;
            }) || x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: x = 1; break-with true;
                case 1: break-with (x = 1) == 1;
                default: break-with false;
            }) || x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break-with true;
                case 1: break-with (x = 1) == 1;
                default: break-with false;
            }) && x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break-with false;
                case 1: break-with isTrue() || (x = 1) == 1;
                default: break-with false;
            }) && x == 1;
        }

        {
            int x;

            t = (switch(a) {
                case 0: break-with false;
                case 1: break-with isTrue() ? true : (x = 1) == 1;
                default: break-with false;
            }) && x == 1;
        }

        {
            final int x;

            t = (switch(a) {
                case 0: break-with false;
                case 1: break-with isTrue() ? true : (x = 1) == 1;
                default: break-with false;
            }) && (x = 1) == 1;
        }
    }

    private static boolean isTrue() {
        return true;
    }

}
