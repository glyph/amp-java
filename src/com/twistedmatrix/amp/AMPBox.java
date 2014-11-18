package com.twistedmatrix.amp;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

/**
 * small ordered key=>value mapping where the keys and values are both byte
 * arrays.
 */

public class AMPBox implements Map<byte[], byte[]> {
    private ArrayList<Pair> pairs;

    public AMPBox() {
        pairs = new ArrayList<Pair>();
    }

    private class Pair implements Map.Entry<byte[], byte[]> {
        Pair(byte[] k, byte[] v) {
            this.key = k;
            this.value = v;
        }
        byte[] key;
        byte[] value;

        public boolean equals(Object o) {
            if (o instanceof Pair) {
                Pair other = (Pair) o;
                return (Arrays.equals(other.key, this.key) &&
                        Arrays.equals(other.value, this.value));
            }
            return false;
        }

        public byte[] getKey() { return key; }
        public byte[] getValue() { return value; }

        public byte[] setValue(byte[] value)
	    throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }
    }

    /* implementation of Map interface */
    public void clear() throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public Set<byte[]> keySet() {
        HashSet<byte[]> hs = new HashSet<byte[]>();
        for (Pair p: pairs) {
            hs.add(p.key);
        }
        return hs;
    }

    public Set<Map.Entry<byte[], byte[]>> entrySet() {
        HashSet<Map.Entry<byte[], byte[]>> hs =
            new HashSet<Map.Entry<byte[], byte[]>>();
        for (Pair p: pairs) {
            hs.add(p);
        }
        return hs;
    }

    public Collection<byte[]> values() {
        ArrayList<byte[]> v = new ArrayList<byte[]>();
        for (Pair p: pairs) {
            v.add(p.value);
        }
        return v;
    }

    public int size() {
        return pairs.size();
    }

    public boolean isEmpty() {
        return 0 == size();
    }

    public boolean equals (Object o) {
        if (!(o instanceof AMPBox)) {
            return false;
        }
        AMPBox other = (AMPBox) o;

        for (Pair p: pairs) {
            if (!Arrays.equals(other.get(p.key), p.value)) {
                return false;
            }
        }
        return true;
    }

    public byte[] put(byte[] key, byte[] value) {
        pairs.add(new Pair(key, value));
        return null;
    }

    public void putAll(Map<? extends byte[], ? extends byte[]> m) {
        for (Map.Entry<? extends byte[], ? extends byte[]> me: m.entrySet()) {
            put(me.getKey(), me.getValue());
        }
    }

    public byte[] remove(Object k) {
        byte[] key = (byte[]) k;
        for (int i = 0; i < pairs.size(); i++) {
            Pair p = pairs.get(i);
            if (Arrays.equals(p.key, key)) {
                pairs.remove(i);
                return p.value;
            }
        }
        return null;
    }

    /**
     * Convenience API because there is no byte literal syntax in java.
     */
    public void put(String key, String value) {
        put(asBytes(key), asBytes(value));
    }

    public void put(String key, byte[] value) {
        put(asBytes(key), value);
    }

    public static byte[] asBytes(String in) {
        return asBytes(in, "ISO-8859-1");
    }

    public static byte[] asBytes(String in, String encoding) {
        try {
            return in.getBytes(encoding);
        } catch (UnsupportedEncodingException uee) {
            throw new Error("JVMs are required to support encoding: "+encoding);
        }
    }

    public static String asString(byte[] in, String knownEncoding) {
        try {
            return new String(in, knownEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new Error("JVMs are required to support this encoding: " +
			    knownEncoding);
        }
    }

    public static String asString(byte[] in) {
        return asString(in, "ISO-8859-1");
    }


    public byte[] get(byte[] key) {
        for(Pair p: pairs) {
            if (Arrays.equals(key, p.key)) {
                return p.value;
            }
        }
        return null;
    }

    public byte[] get(String key) {
        return get(key.getBytes());
    }

    public byte[] get(Object key) {
        if (key instanceof String) {
            return get((String)key);
        } else if (key instanceof byte[]) {
            return get((byte[])key);
        }
        return null;
    }

    public boolean containsValue(Object v) {
        byte[] value = (byte[]) v;
        for (Pair p: pairs) {
            if (Arrays.equals(p.value, value)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsKey(Object value) {
        return null != get(value);
    }

    /**
     * Take the values encoded in this packet and map them into an arbitrary
     * Java object.  This method will fill out fields declared in the given
     * object's class which correspond to types defined in the AMP protocol:
     * integer, unicode string, raw bytes, boolean, float.
     */

    public void fillOut(Object o) {
        Class c = o.getClass();
        Field[] fields = c.getFields();

        try {
            for (Field f: fields) {
                byte[] toDecode = get(f.getName());
                Object decoded = getAndDecode(f.getName(), f.getType());
                if(null != decoded) {
                    f.set(o, decoded);
                }
            }
        } catch (IllegalAccessException iae) {
	  iae.printStackTrace();
            /*
              This should be basically impossible to get; getFields should
              only give us public fields.
             */
        }
    }

    public ErrorPrototype fillError() {
      String code = (String) getAndDecode("_error_code", String.class);
      String description = (String) getAndDecode("_error_description",
						 String.class);

      return new ErrorPrototype(code, description);
    }

    /** Decode incoming data. */
    public Object getAndDecode(String key, Class t) {
        byte[] toDecode = this.get(key);
        if (null != toDecode) {
            if ((t == int.class) || (t == Integer.class)) {
                return Integer.decode(asString(toDecode));
            } else if (t == String.class) {
                return asString(toDecode, "UTF-8");
            } else if ((t == double.class) || (t == Double.class)) {
		String s = asString(toDecode);
		if (s.equals("Inf"))
		    return Double.POSITIVE_INFINITY;
		else if (s.equals("-Inf"))
		    return Double.NEGATIVE_INFINITY;
		else if (s.equals("nan"))
		    return Double.NaN;
		else
		    return Double.parseDouble(s);
            } else if ((t == boolean.class) || (t == Boolean.class)) {
                String s = asString(toDecode);
                if(s.equals("True"))
                    return Boolean.TRUE;
                 else
                    return Boolean.FALSE;
            } else if (t == BigDecimal.class) {
		String s = asString(toDecode);
		if (s.equals("Infinity") || s.equals("-Infinity") ||
		    s.equals("NaN") || s.equals("-NaN") ||
		    s.equals("sNaN") || s.equals("-sNaN"))
		    throw new Error ("Value '" + s + "' is not supported!");
		else
		    return new BigDecimal(s);
	    } else if (t == Calendar.class) {
                String s = asString(toDecode);
		Date date = new Date();
		SimpleDateFormat dtf =
		    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		try {
		    date = dtf.parse(s);
		} catch (ParseException pe) {
		    throw new Error ("Unable to parse date '" + s + "'!");
		}

		Calendar cal = Calendar.getInstance();
		cal.setTime(date);

		if (s.length() == 32) {
		    String tzid = "UTC" + s.substring(26, 32);
		    TimeZone tz = TimeZone.getTimeZone(tzid);
		    cal.setTimeZone(tz);
		}

		return cal;
	    } else if (t == byte[].class) {
                return toDecode;
            }
        }
        return null;
    }

    /** Encode outgoing data. */
    public void putAndEncode(String key, Object o) {
        Class t = o.getClass();
        byte[] value = null;
        if (t == Integer.class) {
            value = asBytes(((Integer)o).toString());
        } else if (t == String.class) {
            value = asBytes((String) o, "UTF-8");
        } else if (t == Double.class) {
	    Double d = (Double) o;
	    if (d.equals(Double.POSITIVE_INFINITY))
		value = asBytes("Inf");
	    else if (d.equals(Double.NEGATIVE_INFINITY))
		value = asBytes("-Inf");
	    else if (d.equals(Double.NaN))
		value = asBytes("nan");
	    else
		value = asBytes(d.toString());
        } else if (t == Boolean.class) {
            if (((Boolean)o).booleanValue()) {
                value = asBytes("True");
            } else {
                value = asBytes("False");
            }
        } else if (t == BigDecimal.class) {
	    value = asBytes(((BigDecimal)o).toString());
	} else if (t == Calendar.class || t == GregorianCalendar.class) {
	    String dir = "+";
	    Calendar cal = (Calendar) o;
	    TimeZone tz = cal.getTimeZone();
	    long tzhours = TimeUnit.MILLISECONDS.toHours(tz.getRawOffset());
	    long tzmins = TimeUnit.MILLISECONDS.toMinutes(tz.getRawOffset())
		- TimeUnit.HOURS.toMinutes(tzhours);
	    if (tzhours < 0) {
		dir = "-";
		tzhours = 0 - tzhours;
	    }

	    String str = String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03d000" +
				       "%s%02d:%02d", cal.get(cal.YEAR),
				       cal.get(cal.MONTH), 
				       cal.get(cal.DAY_OF_MONTH),
				       cal.get(cal.HOUR_OF_DAY),
				       cal.get(cal.MINUTE), cal.get(cal.SECOND),
				       cal.get(cal.MILLISECOND), 
				       dir, tzhours, tzmins);
	    value = asBytes(str);
	} else if (t == byte[].class) {
            value = (byte[]) o;
        }
        if (null != value) {
            put(asBytes(key), value);
        }
    }

    public void extractFrom(Object o) {
        Class c = o.getClass();
        Field[] fields = c.getFields();
        try {
            for (Field f: fields) {
                putAndEncode(f.getName(), f.get(o));
            }
        } catch (IllegalAccessException iae) {
	  iae.printStackTrace();
            /*
              This should be basically impossible to get; getFields should
              only give us public fields.
             */
        }
    }

    public byte[] encode() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (Pair p: pairs) {
            for (byte[] bp : new byte[][] {p.key, p.value}) {
                baos.write(bp.length / 0x100); // DIV
                baos.write(bp.length % 0x100); // MOD
                baos.write(bp, 0, bp.length);
            }
        }
        baos.write(0);
        baos.write(0);
        return baos.toByteArray();
    }
}
