/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang;

import static java.util.Objects.hash;

// @jdk.internal.MigratedValueClass
// @jdk.internal.ValueBased
/**
 * Lorem ipsum.
 *
 * <br>Discussion of (real, imaginary) model, polar coordinates and
 * {@linkplain #proj projection method}, infinities (complex plane vs Riemann sphere),
 * branch cuts, component-wise vs norm-wise error, specific algorithms
 * used subject to change, etc. Policy statement on underlying math library to use.
 *
 * <br>For arithmetic,
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;complex <i>op</i> double
 * <br>or
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;complex <i>op</i> imaginary
 * <br>are generally correctly-rounded;
 * <br>&nbsp;&nbsp;&nbsp;&nbsp;complex <i>op</i> complex
 * <br>specs and implementation are more complicated.
 *
 * @apiNote
 * DISCLAIMER: this is an early iteration of complex/imaginary being
 * circulated to gain initial feedback on the API. A main goal at this
 * stage is to explore and rough-in numerical support. The
 * language-feature modeling aspects of complex/imaginary, plain
 * class, or record, or value record, etc. will be more fully
 * considered in future iterations.
 *
 * <p>It is intentional that neither {@code Complex} nor {@code
 * Imaginary} extend {@code java.lang.Number} and that {@code Complex}
 * does <i>not</i> implement {@code Comparable}. Mathematically,
 * complex numbers are an <em>unordered</em> field. However, it is
 * possible to impose an ordering on {@code Complex} values if
 * desired.
 *
 * <p>Semantically, this class is {@code Complex<double>} where {@code
 * Complex<float>} and {@code Complex<Float16>} would be desired
 * possibilities as well, preferably with some kind of code
 * sharing. However, any such abstraction across floating-point base
 * types is left for future work.
 *
 * <p>This design of complex/imaginary is informed by the support for
 * complex/imaginary in C99 and later versions of the C standard.
 *
 * <p>For explanatory purposes, in the discussions below of the semantics
 * of arithmetic methods, two complex numbers
 * <br>(<i>a</i> + <i>i</i>&middot;<i>b</i>) and (<i>c</i> + <i>i</i>&middot;<i>d</i>)
 * <br>will be used for notational convenience in specifying the
 * calculations used to compute the real and imaginary components.
 */
public final class Complex {
    // Implementation design note: for arithmetic, generally following
    // the set of algorithms that have small component-wise error
    // bounds cited in Muller et al.'s "Handbook of Floating-Point
    // Arithmetic, second edition."
    //
    // The implementation assumes fma runs quickly.
    //
    // Design point: define math library in terms of explicit calls to
    // Math or StrictMath methods?

    private double    real;
    private Imaginary imag;

    /**
     * Lorem ipsum.
     */
    public static final class Imaginary implements Comparable<Imaginary> {
        // Semantically this class is Imaginary<double>.

        private double value;

        private Imaginary(double value) {
            this.value = value;
        }

        /**
         * Lorem ipsum.
         */
        public static final Imaginary ZERO = new Imaginary(0.0);

        /**
         * Lorem ipsum.
         */
        public static final Imaginary I = new Imaginary(1.0);

        /**
         * Lorem ipsum.
         */
        public static final Imaginary INFINITY = new Imaginary(Double.POSITIVE_INFINITY);

        /**

         * {@return an imaginary value whose sign and magnitude are
         * equal to the argument}
         *
         * @param value the the sign and magnitude to use for the
         * imaginary result
         */
        public static Imaginary valueOf(double value) {
            return new Imaginary(value);
        }

        // API note: favor putting overloads of arithmetic operators
        // into Complex.

        /**
         * {@return lorem ipsum}
         *
         * @implSpec
         * {@code Double.compare} the value fields.
         *
         * @param imag the imaginary to be compared
         */
        @Override
        public int compareTo(Imaginary imag) {
            return Double.compare(value, imag.value);
        }

        /**
         * {@return lorem ipsum}
         */
        @Override
        public String toString() {
            return "i*" + value;
        }

        /**
         * {@return lorem ipsum}
         * @param that lorem ipsum}
         */
        @Override
        public boolean equals(Object that) {
            if (that instanceof Imaginary imag) {
                return Double.compare(value, imag.value) == 0;
            } else
                return false;
        }

        /**
         * {@return lorem ipsum}
         * @apiNote
         * Note all NaN values hash to the same value.
         */
        @Override
        public int hashCode() {
            // Placeholder hash function
            return Double.hashCode(value);
        }

        /**
         * {@return lorem ipsum}
         */
        public double getValue() {
            return value;
        }
    }

    private Complex(double real, Imaginary imag) {
        this.real = real;
        this.imag = imag;
    }

    /**
     * Lorem ipsum.
     */
    public static final Complex ZERO = valueOf(+0.0, Imaginary.ZERO);

    /**
     * Lorem ipsum.
     */
    public static final Complex ONE = valueOf(1.0, Imaginary.ZERO);

    /**
     * Lorem ipsum.
     */
    public static final Complex INFINITY = valueOf(Double.POSITIVE_INFINITY, Imaginary.ZERO);

    /**
     * Lorem ipsum.
     */
    public static final Complex NaN = valueOf(Double.NaN, Double.NaN);

    /**
     * {@return the real component of this complex}
     */
    public double real() { // better as a static method?
        return real;
    }

    /**
     * {@return the imaginary component of this complex converted to a {@code double}}
     * @apiNote
     * In the final API, only one of this method and {@code imaginary} may be present.
     */
    public double imag() { // better as a static method?
        return imag.value;
    }

    /**
     * {@return the imaginary component of this complex}
     * @apiNote
     * In the final API, only one of this method and {@code imag} may be present.
     */
    public Imaginary imaginary() { // better as a static method?
        return imag;
    }

    /**
     * {@return a complex value with real and imaginary components
     * equivalent to the real and imaginary arguments, respectively}
     *
     * @param real the real component
     * @param imag the imaginary component
     */
    public static Complex valueOf(double real, Imaginary imag) {
        return new Complex(real, imag);
    }

    // Promote to a full public method?
    /*public*/ static Complex valueOf(double real, double imag) {
        return new Complex(real, Imaginary.valueOf(imag));
    }

    /**
     * {@return a complex value with the real component equivalent to the
     * argument and a {@code +0.0} imaginary component}
     *
     * @param real the real component
     */
    public static Complex valueOf(double real) {
        return new Complex(real, Imaginary.ZERO);
    }

    /**
     * {@return a complex value with a {@code +0.0} real component and an
     * imaginary component equivalent to the argument}
     *
     * @param imag the imaginary component
     */
    public static Complex valueOf(Imaginary imag) {
        return new Complex(0.0, imag);
    }

    /**
     * {@return lorem ipsum}
     *
     * @param s the string to be parsed
     *
     * @see Double#parseDouble(String)
     */
    public static Complex valueOf(String s) {
        return new Complex(0, new Imaginary(0));
    }

    /**
     * {@return lorem ipsum}
     *
     * @apiNote
     * To convert <em>to</em> polar form, use {@code r = Complex.abs(c)} and
     * {@code theta = Math.atan2(c.imag(), c.real())}.
     *
     * @param r the modulus, {@linkplain abs absolute value}, of the
     * complex number in polar form
     * @param theta the phase angle of the complex number in polar form
     *
     * @see Math#atan2(double, double)
     */
    public static Complex valueOfPolar(double r, double theta) {
        return valueOf(r*Math.cos(theta),
                       r*Math.sin(theta));
    }

    // --- negate and conjugate

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * The negation is equivalent to
     * &minus;<i>a</i>&nbsp;+&nbsp;&minus;<i>i</i>&middot;<i>b</i>
     *
     * @param c a complex value
     */
    public static Complex negate(Complex c) {
        return valueOf(-c.real, -c.imag.value);
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * The negation is equivalent to
     * &minus;<i>i</i>&middot;<i>b</i>
     *
     * @param imag an imaginary value
     */
    public static Imaginary negate(Imaginary imag) {
        return Imaginary.valueOf(-imag.value);
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * The conjugate is equivalent to
     * <i>a</i>&nbsp;+&nbsp;&minus;<i>i</i>&middot;<i>b</i>
     *
     * @param c a complex value
     */
    public static Complex conj(Complex c) {
        return valueOf(c.real, -c.imag.value);
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * The conjugate is equivalent to
     * &minus;<i>i</i>&middot;<i>b</i>
     *
     * @param imag an imaginary value
     */
    public static Imaginary conj(Imaginary imag) {
        return Imaginary.valueOf(-imag.value);
    }

    // no-op conj method on double could be added too...

    // ---- Addition operators

    /**
     * Adds two complex values together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * (<i>a</i>&nbsp;+&nbsp;<i>c</i>)&nbsp;+&nbsp;<i>i</i>&middot;(<i>b</i>&nbsp;+&nbsp;<i>d</i>).
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Complex add(Complex addend, Complex augend) {
        double a = addend.real;
        double b = addend.imag.value;
        double c = augend.real;
        double d = augend.imag.value;

        return valueOf(a + c, b + d);
    }

    /**
     * Add a complex value and a {@code double} together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * (<i>a</i>&nbsp;+&nbsp;<i>c</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>b</i>.
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Complex add(Complex addend, double augend) {
        double a = addend.real;
        double b = addend.imag.value;
        double c = augend;
        // d not defined

        return valueOf(a + c, Imaginary.valueOf(b));
    }

    /**
     * Add a {@code double} and a complex value together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * (<i>a</i>&nbsp;+&nbsp;<i>c</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>d</i>.
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Complex add(double addend, Complex augend) {
        double a = addend;
        // b not defined
        double c = augend.real;
        double d = augend.imag.value;

        return valueOf(a + c, d);
    }

    /**
     * Add a complex value and an imaginary together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * <i>a</i>&nbsp;+&nbsp;<i>i</i>&middot;(<i>b</i>&nbsp;+&nbsp;<i>d</i>).
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Complex add(Complex addend, Imaginary augend) {
        double a = addend.real;
        double b = addend.imag.value;
        // c not defined
        double d = augend.value;

        return valueOf(a, b + d);
    }

    /**
     * Add an imaginary and a complex value together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * <i>c</i>&nbsp;+&nbsp;<i>i</i>&middot;(<i>b</i>&nbsp;+&nbsp;<i>d</i>).
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Complex add(Imaginary addend, Complex augend) {
        // a not defined
        double b = addend.value;
        double c = augend.real;
        double d = augend.imag.value;

        return valueOf(c, b + d);
    }

    /**
     * Adds two imaginary values together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * <i>i</i>&middot;(<i>b</i>&nbsp;+&nbsp;<i>d</i>).
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Imaginary add(Imaginary addend, Imaginary augend) {
        // a not defined
        double b = addend.value;
        // c not defined
        double d = augend.value;

        return Imaginary.valueOf(b + d);
    }

    /**
     * Adds an imaginary value and a {@code double} together.
     *
     * @implSpec
     * The computed sum is equivalent to
     * <i>c</i>&nbsp;+&nbsp;<i>i</i>&middot;<i>b</i>
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     */
    public static Complex add(Imaginary addend, double augend) {
        // a not defined
        Imaginary b = addend;
        double    c = augend;
        // d not defined

        return valueOf(c, b);
    }

    /**
     * Adds a {@code double} and an imaginary value.
     *
     * @implSpec
     * The computed sum is equivalent to
     * <i>a</i>&nbsp;+&nbsp;<i>i</i>&middot;<i>d</i>
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the difference of the operands
     */
    public static Complex add(double addend, Imaginary augend) {
        double    a = addend;
        // b not defined
        // c not defined
        Imaginary d = augend;

        return valueOf(a, d);
    }

    // ---- Subtraction operators

    /**
     * Subtracts two complex values.
     *
     * @implSpec
     * The computed difference is equivalent to
     * (<i>a</i>&nbsp;&minus;&nbsp;<i>c</i>)&nbsp;+&nbsp;<i>i</i>&middot;(<i>b</i>&nbsp;&minus;&nbsp;<i>d</i>).
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(Complex minuend, Complex subtrahend) {
        double a = minuend.real;
        double b = subtrahend.imag.value;
        double c = minuend.real;
        double d = subtrahend.imag.value;

        return valueOf(a - c, b - d);
    }

    /**
     * Subtract a complex value and a {@code double}.
     *
     * @implSpec
     * The computed difference is equivalent to
     * (<i>a</i>&nbsp;&minus;&nbsp;<i>c</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>b</i>.
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(Complex minuend, double subtrahend) {
        double a = minuend.real;
        double b = minuend.imag.value;
        double c = subtrahend;
        // d not defined

        return valueOf(a - c, b);
    }

    /**
     * Subtract a {@code double} and a complex value.
     *
     * @implSpec
     * The computed difference is equivalent to
     * (<i>a</i>&nbsp;&minus;&nbsp;<i>c</i>)&nbsp;+&nbsp;&minus;<i>i</i>&middot;<i>d</i>.
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(double minuend, Complex subtrahend) {
        double a = minuend;
        // b not defined
        double c = subtrahend.real;
        double d = subtrahend.imag.value;

        return valueOf(a - c, -d);
    }

    /**
     * Subtract a complex value and an imaginary.
     *
     * @implSpec
     * The computed difference is equivalent to
     * <i>a</i>&nbsp;+&nbsp;<i>i</i>&middot;(<i>b</i>&nbsp;&minus;&nbsp;<i>d</i>).
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(Complex minuend, Imaginary subtrahend) {
        double a = minuend.real;
        double b = minuend.imag.value;
        // c not defined
        double d = subtrahend.value;

        return valueOf(a, b - d);
    }

    /**
     * Subtract an imaginary and a complex value.
     *
     * @implSpec
     * The computed difference is equivalent to
     * &minus;<i>c</i>&nbsp;+&nbsp;<i>i</i>&middot;(<i>b</i>&nbsp;&minus;&nbsp;<i>d</i>).
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(Imaginary minuend, Complex subtrahend) {
        // a not defined
        double b = minuend.value;
        double c = subtrahend.real;
        double d = subtrahend.imag.value;

        return valueOf(-c, b - d);
    }

    /**
     * Subtracts two imaginary values.
     *
     * @implSpec
     * The computed difference is equivalent to
     * <i>i</i>&middot;(<i>b</i>&nbsp;&minus;&nbsp;<i>d</i>).
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Imaginary subtract(Imaginary minuend, Imaginary subtrahend) {
        // a not defined
        double b = minuend.value;
        // a not defined
        double d = subtrahend.value;

        return Imaginary.valueOf(b - d);
    }

    /**
     * Subtracts an imaginary value and a {@code double}.
     *
     * @implSpec
     * The computed difference is equivalent to
     * &minus;<i>c</i>&nbsp;+&nbsp;<i>i</i>&middot;<i>b</i>
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(Imaginary minuend, double subtrahend) {
        // a not defined
        Imaginary b = minuend;
        double    c = subtrahend;
        // d not defined

        return valueOf(-c, b);
    }

    /**
     * Subtracts a {@code double} and an imaginary value.
     *
     * @implSpec
     * The computed difference is equivalent to
     * <i>a</i>&nbsp;+&nbsp;&minus;<i>i</i>&middot;<i>d</i>
     *
     * @param minuend the first operand
     * @param subtrahend the second operand
     * @return the difference of the operands
     */
    public static Complex subtract(double minuend, Imaginary subtrahend) {
        double a = minuend;
        // b not defined
        // c not defined
        double d = subtrahend.value;

        return valueOf(a, -d);
    }

    // ---- Multiplication operators

    /**
     * Multiplies two complex values.
     *
     * @apiNote
     * WARNING: while simple, the calculation technique used by this
     * method is subject to spurious underflow and overflow as well as
     * inaccurate component-wise results.
     *
     * @implSpec
     * The computed product is calculated by
     * (<i>ac</i>&nbsp;&minus;&nbsp;<i>bd</i>)&nbsp;+&nbsp;<i>i</i>&middot;(<i>ad</i>&nbsp;+&nbsp;<i>bc</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Complex multiplyTexbook(Complex multiplier, Complex multiplicand) {
        double a = multiplier.real;
        double b = multiplier.imag.value;
        double c = multiplicand.real;
        double d = multiplicand.imag.value;

        return valueOf(a*c - b*d, a*d + b*c);
    }

    /*
     * See Algorithm 11.1 in Muller -- Kahan
     *
     * Include temporarily for comparison purposes.
     */
    private static double wz_minus_xy_Kahan(double w, double x, double y, double z) {
        double w_hat = x*y;
        double e = Math.fma(-x, y, w_hat);
        double f = w*z - w_hat;
        return f + e;
    }

    /*
     * See Algorithm 11.2 in Muller -- Cornea, Harrison, and Tang algorithm
     *
     * This computation is commutative, unlike Algorithm 11.1 using Kahan
     */
    private static double wz_minus_xy(double w, double x, double y, double z) {
        double pi_1 = w*z;
        double e_1  = Math.fma(w, z, -pi_1);
        double pi_2 = x*y;
        double e_2  =  Math.fma(x, y, -pi_2);
        double pi   = pi_1 - pi_2;
        double e    = e_1 - e_2;
        return pi + e;
    }

    /**
     * Multiplies two complex values.
     *
     * @implSpec
     * The computed product
     * (<i>ac</i>&nbsp;&minus;&nbsp;<i>bd</i>)&nbsp;+&nbsp;<i>i</i>&middot;(<i>ad</i>&nbsp;+&nbsp;<i>bc</i>)
     *  is calculated with 2 {@linkplain Math#ulp(double) ulps}
     *  (units in the last place) of rounding error in each of the
     *  real and imaginary components if the intermediate products do
     *  not overflow.
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Complex multiply(Complex multiplier, Complex multiplicand) {
        double a = multiplier.real;
        double b = multiplier.imag.value;
        double c = multiplicand.real;
        double d = multiplicand.imag.value;

        return valueOf(wz_minus_xy(a,  b, d, c),  // real component:      a*c - b*d
                       wz_minus_xy(a, -b, c, d)); // imaginary component: a*d + b*c
    }

    /**
     * Multiplies a complex value and a {@code double}.
     *
     * @implSpec
     * The computed product
     * (<i>ac</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>bc</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Complex multiply(Complex multiplier, double multiplicand) {
        double a = multiplier.real;
        double b = multiplier.imag.value;
        double c = multiplicand;
        //d not defined

        return valueOf(a*c, b*c);
    }

    /**
     * Multiplies a {@code double} and a complex value.
     *
     * @implSpec
     * The computed product
     * (<i>ac</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>ad</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Complex multiply(double multiplier, Complex multiplicand) {
        double a = multiplier;
        // b not defined
        double c = multiplicand.real;
        double d = multiplicand.imag.value;

        return valueOf(a*c, a*d);
    }

    /**
     * Multiplies a complex value and an imaginary.
     *
     * @implSpec
     * The computed product
     * (&minus;<i>bd</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>ad</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Complex multiply(Complex multiplier, Imaginary multiplicand) {
        double a = multiplier.real;
        double b = multiplier.imag.value;
        // c not defined
        double d = multiplicand.value;

        return valueOf(-b*d, a*d);
    }

    /**
     * Multiplies an imaginary and a complex value.
     *
     * @implSpec
     * The computed product
     * (&minus;<i>bd</i>)&nbsp;+&nbsp;<i>i</i>&middot;<i>bc</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Complex multiply(Imaginary multiplier, Complex multiplicand) {
        // a not defined
        double b = multiplier.value;
        double c = multiplicand.real;
        double d = multiplicand.imag.value;

        return valueOf(-b*d, b*c);
    }

    /**
     * Multiplies two imaginaries.
     *
     * @implSpec
     * The computed product
     * (&minus;<i>bd</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static double multiply(Imaginary multiplier, Imaginary multiplicand) {
        // a not defined
        double b = multiplier.value;
        // c not defined
        double d = multiplicand.value;

        return -b*d;
    }

    /**
     * Multiplies an imaginary and a {@code double}
     *
     * @implSpec
     * The computed product
     * (<i>i</i>&middot;<i>bc</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Imaginary multiply(Imaginary multiplier, double multiplicand) {
        // a not defined
        double b = multiplier.value;
        double c = multiplicand;
        // d not defined

        return Imaginary.valueOf(b*c);
    }

    /**
     * Multiplies a {@code double} and an  imaginary.
     *
     * @implSpec
     * The computed product
     * (<i>i</i>&middot;<i>ad</i>)
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     */
    public static Imaginary multiply(double multiplier, Imaginary multiplicand) {
        double a = multiplier;
        // b not defined
        // c not defined
        double d = multiplicand.value;

        return Imaginary.valueOf(a*d);
    }

    // ---- division operators

    /**
     * Divides two complex values.
     *
     * @apiNote
     * Bad things can happen warning.
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Complex divideTextbook(Complex dividend, Complex divisor) {
        double a = dividend.real;
        double b = dividend.imag.value;
        double c = divisor.real;
        double d = divisor.imag.value;

        double scale = c*c + d*d;

        return valueOf((a*c + b*d)/scale, (b*c - a*d)/scale);
    }

    /**
     * Divides two complex values.
     *
     * @implSpec
     * The computed quotient...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Complex divide(Complex dividend, Complex divisor) {
        double a = dividend.real;
        double b = dividend.imag.value;
        double c = divisor.real;
        double d = divisor.imag.value;

        // Probably use Priest algorithm cited in Muller.
        throw new UnsupportedOperationException();
    }

    // Priest algorithm code from TOMS paper appendix
    //
    //     /*
    //      * Set v[0] + i v[1] := (z[0] + i z[1]) / (w[0] + i w[1]) assuming:
    //      *
    //      * 1. z[0], z[1], w[0], w[1] are all finite, and w[0], w[1] are not
    //      * both zero
    //      *
    //      * 2. "int" refers to a 32-bit integer type, "long long int" refers
    //      * to a 64-bit integer type, and "double" refers to an IEEE 754-
    //      * compliant 64-bit floating point type
    //      */
    //     void cdiv(double *v, const double *z, const double *w)
    //     {
    //         union {
    //             long long int i;
    //             double d;
    //         } aa, bb, cc, dd, ss;
    //         double a, b, c, d, t;
    //         int ha, hb, hc, hd, hz, hw, hs;
    //         /* name the components of z and w */
    //         a = z[0];
    //         b = z[1];
    //         c = w[0];
    //         d = w[1];
    //         /* extract high-order 32 bits to estimate |z| and |w| */
    //         aa.d = a;
    //         bb.d = b;
    //         ha = (aa.i >> 32) & 0x7fffffff;
    //         hb = (bb.i >> 32) & 0x7fffffff;
    //         hz = (ha > hb)? ha : hb;
    //         cc.d = c;
    //         dd.d = d;
    //         hc = (cc.i >> 32) & 0x7fffffff;
    //         hd = (dd.i >> 32) & 0x7fffffff;
    //         hw = (hc > hd)? hc : hd;
    //         /* compute the scale factor */
    //         if (hz < 0x07200000 && hw >= 0x32800000 && hw < 0x47100000) {
    //             /* |z| < 2^-909 and 2^-215 <= |w| < 2^114 */
    //             hs = (((0x47100000 - hw) >> 1) & 0xfff00000) + 0x3ff00000;
    //         } else
    //             hs = (((hw >> 2) - hw) + 0x6fd7ffff) & 0xfff00000;
    //         ss.i = (long long int) hs << 32;
    //         /* scale c and d, and compute the quotient */
    //         c *= ss.d;
    //         d *= ss.d;
    //         t = 1.0 / (c * c + d * d);
    //         c *= ss.d;
    //         d *= ss.d;
    //         v[0] = (a * c + b * d) * t;
    //         v[1] = (b * c - a * d) * t;
    //     }

    /**
     * Divides a complex value by a {@code double}.
     *
     * @implSpec
     * The computed quotient...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Complex divide(Complex dividend, double divisor) {
        double a = dividend.real;
        double b = dividend.imag.value;
        double c = divisor;
        // d not dfined

        return valueOf(a/c, b/c);
    }

    /**
     * Divides a {@code double} by a complex value.
     *
     * @implSpec
     * The computed quotient ...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Complex divide(double dividend, Complex divisor) {
        double a = dividend;
        // b not defined
        double c = divisor.real;
        double d = divisor.imag.value;

        throw new UnsupportedOperationException();
    }

    /**
     * Divides a complex value by an imaginary.
     *
     * @implSpec
     * The computed quotient...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Complex divide(Complex dividend, Imaginary divisor) {
        double a = dividend.real;
        double b = dividend.imag.value;
        // c not defined
        double d = divisor.value;

        return valueOf(b/d, -a/d);
    }

    /**
     * Divides an imaginary by a complex value.
     *
     * @implSpec
     * The computed quotient ...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Complex divide(Imaginary dividend, Complex divisor) {
        // a not defined
        double b = dividend.value;
        double c = divisor.real;
        double d = divisor.imag.value;

        throw new UnsupportedOperationException();
    }

    /**
     * Divides one imaginary value by another.
     *
     * @implSpec
     * The computed quotient ...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static double divide(Imaginary dividend, Imaginary divisor) {
        // a not defined
        double b = dividend.value;
        // c not defined
        double d = divisor.value;

        return b/d;
    }

    /**
     * Divides an imaginary value by a {@code double}
     *
     * @implSpec
     * The computed quotient ...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Imaginary divide(Imaginary dividend, double divisor) {
        // a not defined
        double b = dividend.value;
        double c = divisor;
        // d not defined

        return Imaginary.valueOf(b/c);
    }

    /**
     * Divides a @code double} by an imaginary.
     *
     * @implSpec
     * The computed quotient ...
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     */
    public static Imaginary divide(double dividend, Imaginary divisor) {
        double a = dividend;
        // b not defined
        // c not defined
        double d = divisor.value;

        return Imaginary.valueOf(-a/d);
    }

    // csqrt => sqrt(hypot(x,y)) * (cos(atan2(y,x)/2) +i * sin(atan2(y,x)/2))

    // From C99 rationale:
    // Hence [with the expression above] the positive points on the
    // branch cut on the negative x-axis map to the positive y-axis,
    // and the negative points on the branch cut on the negative
    // x-axis map to the negative y-axis

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param radicand lorem ipsum
     * @see Math#sqrt(double)
     */
    Complex sqrt(Complex radicand) {
        double x = radicand.real;
        double y = radicand.imag.value;

        double scale = Math.sqrt(Math.hypot(x,y));
        return valueOf(scale*Math.cos(Math.atan2(y,x)*0.5),
                       scale*Math.sin(Math.atan2(y,x)*0.5));
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * use hypot
     * @param c a complex value
     * @see Math#abs(double)
     */
    public static double abs(Complex c) {
        double a = c.real;
        double b = c.imag.value;
        return StrictMath.hypot(a, b);
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * use Math.abs
     * @param imag an imaginary value
     * @see Math#abs(double)
     */
    public static double abs(Imaginary imag) {
        return Math.abs(imag.value);
    }

    // Math library methods

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#cos(double)
     */
    public static Complex cos(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        return valueOf( Math.cos(x)*Math.cosh(y),
                       -Math.sin(x)*Math.sinh(y));
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * use cosh(b)
     * @param imag an imaginary value
     * @see Math#cos(double)
     */
    public static double cos(Imaginary imag) {
        return Math.cosh(imag.value);
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#sin(double)
     */
    public static Complex sin(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        return valueOf(Math.sin(x)*Math.cosh(y),
                       Math.cos(x)*Math.sinh(y));
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * use sinh(b)
     * @param imag an imaginary value
     * @see Math#sin(double)
     */
    public static Imaginary sin(Imaginary imag) {
        return Imaginary.valueOf(Math.sinh(imag.value));
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#tan(double)
     */
    public static Complex tan(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        double divisor = Math.cos(2*x) + Math.cosh(2*y);
        return valueOf( Math.sin(2*x)/divisor,
                       Math.sinh(2*y)/divisor);
    }

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * use tanh(b)
     * @param imag an imaginary value
     * @see Math#tan(double)
     */
    public static Imaginary tan(Imaginary imag) {
        return Imaginary.valueOf(Math.tanh(imag.value));
    }

    /**
     * {@return the hyperbolic cosine of a {@code complex} value}
     *
     * Mathematically, the hyperbolic cosine of <i>x</i> is defined to be
     * (<i>e<sup>x</sup>&nbsp;+&nbsp;e<sup>&minus;x</sup></i>)/2
     * where <i>e</i> is {@linkplain Math#E Euler's number}.
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#cosh(double)
     */
    public static Complex cosh(Complex c) {
        // cosh(-x) = cosh(x)
        double x = c.real;
        double y = c.imag.value;

        return valueOf(Math.cosh(x)*Math.cos(y),
                       Math.sinh(x)*Math.sin(y));
    }

    /**
     * {@return the hyperbolic cosine of an imaginary value}
     *
     * @implSpec
     * use cos(b)
     * @param imag an imaginary value
     * @see Math#cosh(double)
     */
    public static double cosh(Imaginary imag) {
        return Math.cos(imag.value);
    }

    /**
     * {@return the hyperbolic sine of a {@code complex} value}
     *
     * Mathematically, the hyperbolic sine of <i>x</i> is defined to be
     * (<i>e<sup>x</sup>&nbsp;&minus;&nbsp;e<sup>&minus;x</sup></i>)/2
     * where <i>e</i> is {@linkplain Math#E Euler's number}.
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#sinh(double)
     */
    public static Complex sinh(Complex c) {
        // sinh(-x) = -sinh(x)
        double x = c.real;
        double y = c.imag.value;

        return valueOf(Math.sinh(x)*Math.cos(y),
                       Math.cosh(x)*Math.sin(y));
    }

    /**
     * {@return the hyperbolic sine of an imaginary value}
     *
     * @implSpec
     * use sin(b)
     * @param imag an imaginary value
     * @see Math#sinh(double)
     */
    public static  Imaginary sinh(Imaginary imag) {
        return Imaginary.valueOf(StrictMath.sin(imag.value));
    }

    /**
     * {@return the hyperbolic tangent of a {@code complex} value}
     *
     * The hyperbolic tangent of <i>x</i> is defined to be
     * (<i>e<sup>x</sup>&nbsp;&minus;&nbsp;e<sup>&minus;x</sup></i>)/(<i>e<sup>x</sup>&nbsp;+&nbsp;e<sup>&minus;x</sup></i>),
     * in other words, {@linkplain Complex#sinh
     * sinh(<i>x</i>)}/{@linkplain Complex#cosh cosh(<i>x</i>)}.
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#tanh(double)
     */
    public static Complex tanh(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        double divisor = Math.cosh(2*x) + Math.cos(2*y);
        return valueOf(Math.sinh(2*x)/divisor,
                        Math.sin(2*y)/divisor);
    }

    /**
     * {@return lorem ipsum}
     *
     * @param imag an imaginary value
     * @see Math#tanh(double)
     */
    public static Imaginary tanh(Imaginary imag) {
        return Imaginary.valueOf(StrictMath.tan(imag.value));
    }


    // exp -- exp(a + i*b) = exp(a)(cos(b) + i*sin(b))
    // e^x = cosh(x) + sinh(x)
    // mathematically, e^(a+ib)  = (cosh(a) + sinh(z)*(cos(b)+i*sin(b))

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#exp(double)
     */
    public static Complex exp(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        double exp_x = Math.exp(x);

        return valueOf(exp_x*Math.cos(y),
                       exp_x*Math.sin(y));
    }

    // log -- ln(a + i*b) = ln(abs(x)) + i*(pi/2)*[1 - sgn(x)], x != 0
    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#log(double)
     */
    public static Complex log(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        return valueOf(Math.log(Math.hypot(x,y)),
                       Math.atan2(y,x) );
    }

    // cpow(w,z) is cexp(z * clog(w))

    /**
     * {@return lorem ipsum}
     *
     * @implSpec
     * For now, use C99 rationale formula
     *
     * @param c a complex value
     * @see Math#pow(double, double)
     */
    public static Complex pow(Complex c) {
        double x = c.real;
        double y = c.imag.value;

        return exp(multiply(c.imag, log(valueOf(x))));
    }

    // Utility methods

    /**
     * {@return lorem ipsum}
     *
     * @param c a complex value
     */
    public static Complex proj(Complex c) {
        return isInfinite(c) ? INFINITY : c;
    }

    /**
     * {@return {@code true} if <em>both</em> the real <em>and</em>
     * imaginary components are zero; {@code false} otherwise}
     *
     * @param c a complex value
     */
    public static boolean isZero(Complex c) {
        return c.real       == 0.0 &&
               c.imag.value == 0.0;
    }

    /**
     * {@return {@code true} if <em>either</em> the real <em>or</em>
     * imaginary component is NaN; {@code false} otherwise}
     *
     * @param c a complex value
     */
    public static boolean isNaN(Complex c) {
        return Double.isNaN(c.real) || Double.isNaN(c.imag.value);
    }

    /**
     * {@return {@code true} if <em>either</em> the real <em>or</em>
     * imaginary component is infinite; {@code false} otherwise}
     *
     * @param c a complex value
     */
    public static boolean isInfinite(Complex c) {
        return Double.isInfinite(c.real) || Double.isInfinite(c.imag.value);
    }

    /**
     * {@return {@code true} if <em>both</em> the real <em>and</em>
     * imaginary components are finite; {@code false} otherwise}
     *
     * @param c a complex value
     */
    public static boolean isFinite(Complex c) {
        return Double.isFinite(c.real) && Double.isFinite(c.imag.value);
    }

    // Object methods

    /**
     * {@return a string representation of this complex value}
     * The representation is of the form:
     * <br>{@code (}<i>a</i>&nbsp;{@code + i*}<i>b</i>{@code )}
     * <br>where <i>a</i> and <i>b</i> are the string representations of
     * the {@linkplain #real() real} and {@linkplain #imag()
     * imaginary} components of this complex.
     */
    @Override
    public String toString() {
        return "(" + real + " + " + imag.toString()  + ")";
    }

    /**
     * {@return {@code true} if the argument is non-null complex value
     * that is {@linkplain #equivalent(Complex, Complex) equivalent}
     * to this complex value; {@code false} otherwise}
     *
     * @apiNote
     * Discuss NaNs
     *
     * @param that the object to compare against
     */
    @Override
    public boolean equals(Object that) {
        if (that instanceof Complex c) {
            return equivalent(this, c);
        } else {
            return false;
        }
    }

    /**
     * {@return {@code true} if the corresponding real and imaginary
     * components of the two arguments have {@linkplain
     * Double##repEquivalence representation equivalence} with each
     * other; {@code false} otherwise}
     *
     * @implSpec
     * This method is operationally equivalent to the expression:
     * {@snippet lang = "java":
     * Double.compare(c1.real(), c2.real()) == 0 &&
     * Double.compare(c1.imag(), c2.imag()) == 0
     * }
     *
     * @param c1 one complex value
     * @param c2 another complex value
     */
    public static boolean equivalent(Complex c1, Complex c2) {
        return Double.compare(c1.real(), c2.real()) == 0 &&
               Double.compare(c1.imag(), c2.imag()) == 0;
    }

    /**
     * {@return a hash code that is a function of the real and
     * imaginary components}
     *
     * @apiNote
     * Discuss NaNs
     */
    @Override
    public int hashCode() {
        // Placeholder hash function
        return hash(Double.hashCode(real), imag.hashCode());
    }
}
