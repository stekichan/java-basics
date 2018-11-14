import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Array;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.nio.file.*;

public class JsonUtils {
	public enum JElemType {
		JT_OBJ,
			JT_ARRAY,
			JT_STRING,
			JT_NUM,
			JT_BOOL,
			JT_INVAL;
	}
	static abstract class JElem {
		JElemType      je_type;
		// String holding the JSON element
		String         je_content;
		// Name in the parent object
		String         je_name;
		// The parent
		String         je_parent;
		// Starting index in parent
		int            je_start;
		// Length of the string.
		int            je_len;
		JElem(int obj_start, String name, String parent)
		{
			this.je_parent = parent;
			this.je_start  = obj_start;
			this.je_name   = name;
		}
		abstract void je_len_calc();
		abstract void je_val_extract();
	}

	static Matcher ju_pattern_search(String search_string, String parent,
					 int start, int end)
	{
		Pattern pattern = Pattern.compile(search_string);

		return  pattern.matcher(parent.substring(start, end));
	}

	static String content_process(String input)
	{
		int    i;
		int    j;
		char[] output = new char[input.length()];

		for (i = 0, j = 0; i < input.length(); ++i) {
			// Skip the quotes at the start and end
			if (input.charAt(i) == '"' &&
			    (i == 0 || i == input.length() - 1))
				continue;
			// Only handles the case of \" and \\.
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
			int i;
			String parent = this.je_parent;

			// Leaving out the case of exponentials and negative
			String search_string = "-?[1-9]\\d*|0";
			Pattern pattern = Pattern.compile(search_string);
			Matcher matcher = pattern.matcher(parent.substring(this.je_start,
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
			int i;
			int colon_idx;
			// Borrowed from https://stackoverflow.com/questions/2498635/java-regex-for-matching-quoted-string-with-escaped-quotes/2498670
			String  search_string ="'([^\\\\']+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*'|\"([^\\\\\"]+|\\\\([btnfr\"'\\\\]|[0-3]?[0-7]{1,2}|u[0-9a-fA-F]{4}))*\"";
			Pattern pattern = Pattern.compile(search_string);
			Matcher matcher;
			i = this.je_start;
			// Get over the name of the field.
			while (i < this.je_parent.length() &&
			       this.je_parent.charAt(i) != ':')
				++i;
			if (i < this.je_parent.length()) {
				colon_idx = i;
				matcher = pattern.matcher(this.je_parent.substring(colon_idx, this.je_parent.length()));
				if (matcher.find()) {
					this.je_content = content_process(matcher.group());
					return;
				}
			}
			this.je_content = "Input string is invalid";
			return;
		}
	}

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
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == '[')
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
				if (this.je_parent.charAt(i) == '[')
					++bcount;
				else if (this.je_parent.charAt(i) == ']')
					--bcount;
				if (bcount == 0)
					break;
				++i;
			}
			if (bcount != 0) {
				return;
			}
			this.je_len = i - this.je_start + 1;
		}
		void je_val_extract()
		{
			int    i;
			String parent = this.je_parent;
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
			int    i;
			String parent = this.je_parent;
			//String  search_string = "\[[^\[]*\]]";
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

		matcher.find();

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
		// Assumed that there is no white-space after "
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
		Boolean   found;

		found = matcher.find();
		if (!found) {
			return JElemType.JT_INVAL;
		}
		// Remove the white space till the first non-trivial character
		i = matcher.end();
		while (i < json.length() &&
		       json.charAt(i) == ' ' || json.charAt(i) == '\t') {
			++i;
		}
		if (i == json.length()) {
			obj_type = JElemType.JT_INVAL;
			return obj_type;
		} else if (json.charAt(i) == '{')
			obj_type = JElemType.JT_OBJ;
		else if (json.charAt(i) == '[')
			obj_type = JElemType.JT_ARRAY;
		else if (json.charAt(i) == '"')
			obj_type = JElemType.JT_STRING;
		else if (is_bool(json, i))
			obj_type = JElemType.JT_BOOL;
		else if (is_num(json, i))
			obj_type = JElemType.JT_NUM;
		else {
			obj_type = JElemType.JT_INVAL;
		}
		return obj_type;
	}

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
		// Check if the required object is present.
		//if (!is_valid_member(data, "formed"))
		//	return null;
		// Identify the type of the required object.
		obj_type = elem_type_identify(data, field_name);
		//if (obj_type == JElemType.JT_INVAL)
		//	return null;
		// Create an instance of required object type.
		obj = json_elem_create(data, field_name, obj_type);
		if (obj == null)
			System.out.println("invalid input string");
		else
			System.out.println(obj.je_content);
	}
}
