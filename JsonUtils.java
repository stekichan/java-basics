/*
 * 
 *
 * Coding style:
 * -Tabs: 8 spaces.
 * -Contents of a wrapped up bracket are aligned
 *  with open bracket on previous line.
 * -Members of a class start with prefix containing
 *  abbreviation of class name.
 *
 */


import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;

import java.nio.file.*;

/**
 *  A class that offers APIs for parsing data from a JSON stirng. To fetch a
 *  specified JSON element from the input, the following approach is used:
 *  1. identify the type of JSON element. This can be either:
 *      a. another JSON object.
 *      b. an array (comma separated JSON objects).
 *      c. a string (quoted expression).
 *      d. a numeric value.
 *      e. a boolean value (either of true/false).
 *  2. extract the contents of element. Method to extract contents of element
 *     are dependent upon the type of the element.
 */
public class JsonUtils {

	/**
	 * Types of JSON elements.
	 */
	public enum JElemType {
		JT_OBJ,
		JT_ARRAY,
		JT_STRING,
		JT_NUM,
		JT_BOOL,
		JT_INVAL;
	}

	/**
	 * A class holding information relevant to JSON element.
	 */
	static abstract class JElem {
		JElemType      je_type;
		/* String holding the JSON element. */
		String         je_content;
		/* Name in the parent object. */
		String         je_name;
		/* The parent string.*/
		String         je_parent;
		/* Starting index in parent string. */
		int            je_start;
		/* Length of the string. */
		int            je_len;
		JElem(int obj_start, String name, String parent)
		{
			this.je_parent = parent;
			this.je_start  = obj_start;
			this.je_name   = name;
		}
		/**
		 * For some of the types (JT_ARRAY, JT_OBJ) it's necessary
		 * to know the length of the element in terms of the number
		 * of characters. This is a no-op method for remaining elements.
		 */
		abstract void je_len_calc();

		/**
		 * Sets je_content.
		 */
		abstract void je_val_extract();
	}

	/**
	 * A helper to search a given string into the parent object of JSON element.
	 * @param search_string: Input string.
	 * @param parent       : Parent string to be searched in.
	 * @param start        : Together with end defines the search range in "parent".
	 * @param end
	 * @return             : A "matcher" associated with the searched string.
	 */
	static Matcher ju_pattern_search(String search_string, String parent,
					 int start, int end)
	{
		Pattern pattern = Pattern.compile(search_string);

		return  pattern.matcher(parent.substring(start, end));
	}

	/**
	 * Eliminates quotes from start and end. For quotes inbetween eliminates
	 * the marker "\" preceding a quote.
	 * @param input: Input string.
	 * @return
	 */
	static String ju_content_process(String input)
	{
		int    i;
		int    j;
		char[] output = new char[input.length()];

		for (i = 0, j = 0; i < input.length(); ++i) {
			/* Skip the quotes at the start and end */
			if (input.charAt(i) == '"' &&
			    (i == 0 || i == input.length() - 1))
				continue;
			/* Only handles the case of \" and \\ */
			if (input.charAt(i) == '\\')
				continue;
			output[j] = input.charAt(i);
			++j;
		}
		return new String(output);
	}

	static class bool_elem extends JElem {
		bool_elem(int obj_start, String name, String parent)
		{
			super(obj_start, name, parent);
			this.je_type = super.je_type.JT_BOOL;
			je_len_calc();
			je_val_extract();
		}

		void je_len_calc()
		{
		// Not required
		}

		void je_val_extract()
		{
			int     i;
			int     comma_idx;
			int     nxt_line_idx;
			int     min_idx;
			Matcher matcher;
			String  parent = this.je_parent;

			/*
			 * Two possible terminations that we take into
			 * consideration are comma and next-line.
			 */
			matcher = ju_pattern_search(",", parent,
						    this.je_start,
						    parent.length() - 1);
			if (matcher.find())
				comma_idx = matcher.start();
			else
				comma_idx = Integer.MAX_VALUE;

			matcher = ju_pattern_search("\n", parent,
						    this.je_start,
						    parent.length() - 1);

			if (matcher.find())
				nxt_line_idx = matcher.start();
			else
				nxt_line_idx = Integer.MAX_VALUE;
			if (nxt_line_idx == comma_idx) {
				this.je_content = "Input string is invalid";
				return;
			}
			/* The first one terminates the value. */
			min_idx = Math.min(comma_idx, nxt_line_idx);
			matcher = ju_pattern_search("(true|false)", parent,
						    this.je_start,
						    this.je_start + min_idx);
			if (matcher.find()) {
				this.je_content = matcher.group();
				return;
			}
			this.je_content = "Input string is invalid";
			return;
		}
	}

	static class num_elem extends JElem {
		public num_elem(int obj_start, String name, String parent)
		{
			super(obj_start, name, parent);
			this.je_type = super.je_type.JT_NUM;
			je_len_calc();
			je_val_extract();
		}

		void je_len_calc()
		{
			// Not required.
		}

		void je_val_extract()
		{
			int     i;
			String  parent = this.je_parent;

			/**
			 * Leaving out the case of exponentials and floating point numbers.
			 * Regex borrowed from: https://stackoverflow.com/questions/2367381/how-to-extract-numbers-from-a-string-and-get-an-array-of-ints
			 **/
			String  search_string = "-?[1-9]\\d*|0";
			Pattern pattern       = Pattern.compile(search_string);
			Matcher matcher       = pattern.matcher(parent.substring(this.je_start,
							  	parent.length() - 1));
			if (matcher.find()) {
				this.je_content = matcher.group();
			} else
				this.je_content = "Input string is invalid";
		}
	}
	static class string_elem extends JElem {
		public string_elem(int obj_start, String name, String parent)
		{
			super(obj_start, name, parent);
			this.je_type = super.je_type.JT_STRING;
			je_len_calc();
			je_val_extract();
		}

		void je_len_calc()
		{
			// Not required
		}

		void je_val_extract()
		{
			int     i;
			int     colon_idx;
			/* Borrowed from https://stackoverflow.com/questions/2498635/java-regex-for-matching-quoted-string-with-escaped-quotes/2498670 */
			String  search_string ="'([^\\\\']+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*'|\"([^\\\\\"]+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*\"";
			Pattern pattern = Pattern.compile(search_string);
			Matcher matcher;

			i = this.je_start;
			/* Get over the name of the field. */
			while (i < this.je_parent.length() &&
			       this.je_parent.charAt(i) != ':')
				++i;
			if (i < this.je_parent.length()) {
				colon_idx = i;
				matcher = pattern.matcher(this.je_parent.substring(colon_idx, this.je_parent.length()));
				if (matcher.find()) {
					this.je_content = ju_content_process(matcher.group());
					return;
				}
			}
			this.je_content = "Input string is invalid";
			return;
		}
	}

	/**
	 * A class for elements of type array.
	 */
	static class arr_elem extends JElem {
		public arr_elem(int obj_start, String name, String parent)
		{
			super(obj_start, name, parent);
			this.je_type = super.je_type.JT_ARRAY;
			je_len_calc();
			je_val_extract();
		}

		void je_len_calc()
		{
			int i;
			int bcount;

			i = this.je_start;
			/*
			 * Search the starting marker of array.
			 *
			 * @todo: Handle the case of invalid JSON
			 * format in which non-space characters are
			 * present in ':' and '['
			 */
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == '[')
					break;
				++i;
			}
			if (i == this.je_parent.length()) {
				this.je_len = -1;
				return;
			}
			/*
			 * bcount acts as a stack, increment is equivalent
			 * to push and decrement to pop.
			 */
			bcount = 1;
			++i;
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == '[')
					++bcount;
				else if (this.je_parent.charAt(i) == ']')
					--bcount;
				/*
				 * We might have an inappropriate ']' placed
				 * at (i+1)st location, but we do not bother
				 * to handle it. Eg. [ ]]
				 */
				if (bcount == 0)
					break;
				++i;
			}
			if (bcount != 0) {
				this.je_len = -1;
				System.out.println("']' missing in array definition");
				return;
			}
			this.je_len = i - this.je_start + 1;
		}

		void je_val_extract()
		{
			int     i;
			String  parent = this.je_parent;
			String  search_string = "\\[";
			Pattern pattern = Pattern.compile(search_string);
			Matcher matcher = pattern.matcher(parent.substring(this.je_start,
					  				   parent.length()));
			if (matcher.find()) {
				this.je_content =
					parent.substring(this.je_start + matcher.start(),
							 this.je_start + this.je_len);
			} else
				System.out.println("Invalid JSON format");
		}

	}

	static class obj_elem extends JElem {
		public obj_elem(int obj_start, String name, String parent)
		{
			super(obj_start, name, parent);
			this.je_type = super.je_type.JT_ARRAY;
			je_len_calc();
			je_val_extract();
		}

		/* @todo: Factor out a function common to both array and a general object. */
		void je_len_calc()
		{
			int i;
			int bcount;

			i = this.je_start;
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == '{')
					break;
				++i;
			}
			if (i == this.je_parent.length()) {
				this.je_len = -1;
				return;
			}
			bcount = 1;
			++i;
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == '{')
					++bcount;
				else if (this.je_parent.charAt(i) == '}')
					--bcount;
				if (bcount == 0)
					break;
				++i;
			}
			if (bcount != 0) {
				System.out.println("Invalid format for " + this.je_name);
				return;
			}
			this.je_len = i - this.je_start + 1;
		}

		void je_val_extract()
		{
			int     i;
			String  parent = this.je_parent;
			String  search_string = "\\{";
			Pattern pattern = Pattern.compile(search_string);
			Matcher matcher = pattern.matcher(parent.substring(this.je_start,
									   parent.length()));
			if (matcher.find()) {
				this.je_content =
					parent.substring(this.je_start + matcher.start(), this.je_start + this.je_len);
			}
		}
	}

	public static JElem json_elem_create(String json, String obj_name,
					     				 JElemType obj_type)
	{
		String  search_string = "\"" + obj_name + "\"" + ":";
		Pattern pattern = Pattern.compile(search_string);
		Matcher matcher = pattern.matcher(json);
		JElem   obj;
		Boolean found;


		found = matcher.find();
		/*
		 * Since object type has been identified existence of obj-name is
		 * guaranteed to be present.
		 */
		assert found;

		switch (obj_type) {
			case JT_BOOL:
				obj = new bool_elem(matcher.start(), obj_name,
						    json);
				break;
			case JT_NUM:
				obj = new num_elem(matcher.start(),
						   obj_name, json);
				break;
			case JT_STRING:
				obj = new string_elem(matcher.start(), obj_name,
						      json);
				break;
			case JT_ARRAY:
				obj = new arr_elem(matcher.start(), obj_name,
						   json);
				break;
			case JT_OBJ:
				obj = new obj_elem(matcher.start(), obj_name,
						   json);
				break;
			default:
				System.out.println("Invalid input type");
				return null;
		}
		return obj;
	}

	private static Boolean is_valid_member(String json, String obj_name)
	{
		/*
		 * Assumed that there is no white-space after \".
		 * @todo: Use a proper regex instead.
		 */
		String  search_string = "\"" + obj_name + "\"" + ":";
		Pattern pattern       = Pattern.compile(search_string);
		Matcher matcher       = pattern.matcher(json);
		return matcher.find();
	}

	private static Boolean is_bool(String json, int i)
	{
		Matcher matcher = ju_pattern_search("true|false", json, i,
						    i + 5);
		return matcher.find();
	}

	private static Boolean is_num(String json, int i)
	{
		return json.charAt(i) == '-' ||
		   (json.charAt(i) >= '0' && json.charAt(i) <= '9');
	}

	public static JElemType elem_type_identify(String json,  String obj_name)
	{
		int       i;
		JElemType obj_type;
		String    search_string = "\"" + obj_name + "\"" + ":";
		Pattern   pattern       = Pattern.compile(search_string);
		Matcher   matcher       = pattern.matcher(json);

		if (!matcher.find())
			return JElemType.JT_INVAL;
		/* Remove the white space till the first non-trivial character. */
		i = matcher.end();
		while (i < json.length() &&
		       json.charAt(i) == ' ' || json.charAt(i) == '\t') {
			++i;
		}
		if (i == json.length())
			obj_type = JElemType.JT_INVAL;
		 else if (json.charAt(i) == '{')
			obj_type = JElemType.JT_OBJ;
		else if (json.charAt(i) == '[')
			obj_type = JElemType.JT_ARRAY;
		else if (json.charAt(i) == '"')
			obj_type = JElemType.JT_STRING;
		else if (is_bool(json, i))
			obj_type = JElemType.JT_BOOL;
		else if (is_num(json, i))
			obj_type = JElemType.JT_NUM;
		else
			obj_type = JElemType.JT_INVAL;
		return obj_type;
	}

	public static String obj_fetch(String json, String obj_name) {
		JElemType obj_type;
		JElem     obj;

		obj_type = elem_type_identify(json, obj_name);
		if (obj_type == JElemType.JT_INVAL)
			return null;
		obj = json_elem_create(json, obj_name, obj_type);
		if (obj == null)
			return null;
		return obj.je_content;
	}
	/**
	 * Trims the starting and ending square brackets.
         */
	public static String array_process(String array) {
		/* Borrowed from https://stackoverflow.com/questions/2498635/java-regex-for-matching-quoted-string-with-escaped-quotes/2498670 */
		String  search_string = "'([^\\\\']+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*'|\"([^\\\\\"]+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*\"";
		Pattern pattern       = Pattern.compile(search_string);
		Matcher matcher       = pattern.matcher(array);
		String  output        = "Not available";

		while (matcher.find()) {
			if (output.equals("Not available"))
				output = matcher.group();
			else
				output = output + ", " + matcher.group();
		}
		return output;
	}

	/**
	 * Fetches attributes from the JSON string for a sandwich and
	 * populates the sandwich object with them.
	 * @param json
	 * @return
	 */

	public static void main(String[] args) throws Exception
        {   
                String data;
                JElem     obj;
                JElemType obj_type;
                String field_name;
                int i;

                if (args.length != 2) {
                        System.out.println("Usage: java JsonUtils <file-name> <filed-name>"); 
                        return;
                }   
                data = new String(Files.readAllBytes(Paths.get(args[0])));
                field_name = args[1];
                /* Identify the type of the required object. */
                obj_type = elem_type_identify(data, field_name);
                if (obj_type == JElemType.JT_INVAL) {
                        System.out.println("invalid object type.");
                        return;
                }   
                /* Create an instance of required object type. */
                obj = json_elem_create(data, field_name, obj_type);
                if (obj == null)
                        return;
                System.out.println(obj.je_content);
        }
}
