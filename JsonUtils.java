import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.file.*;;

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
			int i;

			i = this.je_start;
			// Both "true" and "false" end in "e".
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == 'e')
					break;
				++i;
			}
			if (i == this.je_parent.length())
				this.je_len = -1;
			else
				this.je_len = i - this.je_start + 1;
		}
		void je_val_extract()
		{
			int i;

			i = this.je_start;
			while (i < this.je_parent.length()) {
				if (this.je_parent.substring(i, i + 4).equals("true") ||
			            this.je_parent.substring(i, i + 5).equals("false"))
					break;
				++i;
			}
			if (i == this.je_parent.length())
				; // handle error
			if (this.je_parent.charAt(i) == 't')
				this.je_content = "true";
			else
				this.je_content = "false";
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
			int i;

			i = this.je_start;
			while (i < this.je_parent.length()) {
				if (this.je_parent.charAt(i) == ',' ||
				    this.je_parent.charAt(i) == '\n')
					break;
				++i;
			}
			if (i == this.je_parent.length())
				this.je_len = -1;
			else
				this.je_len =  i - this.je_start;
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
			}
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
			int i;

			i = this.je_start;
			while (i < this.je_parent.length()) {
				if ((this.je_parent.charAt(i) == ',' &&
				      this.je_parent.charAt(i - 1) == '\"' &&
				      i != this.je_start + 1) ||
				    this.je_parent.charAt(i) == '\n')
					break;
				++i;
			}
			if (i == this.je_parent.length())
				this.je_len = -1;
			else
				this.je_len =  i - this.je_start;
		}
		void je_val_extract()
		{
			int    i;
			String parent = this.je_parent;
			//borrowed from https://stackoverflow.com/questions/1473155/how-to-get-data-between-quotes-in-java
			String  search_string = "\"([^\"]*)\"";
			Pattern pattern = Pattern.compile(search_string);
			Matcher matcher = pattern.matcher(parent.substring(this.je_start,
							  parent.length() - 1));
			// Skip the name of the field
			matcher.find();
			if (matcher.find()) {
				this.je_content = matcher.group();
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
		num_elem nobj;

		matcher.find();

		switch (obj_type) {
			case JT_BOOL:
				obj =  new bool_elem(matcher.start(), obj_name,
						     json);
				break;
			case JT_NUM:
				nobj =  new num_elem(matcher.start(),
						    obj_name, json);
				obj = nobj;
				break;
			case JT_STRING:
				obj = new string_elem(matcher.start(), obj_name,
						      json);

				break;
			default:
				obj =  new bool_elem(matcher.start(), obj_name,
						     json);
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
		String str = json.substring(i, i + 3);

		if (json.substring(i, i + 3) == "true")
			return true;
		else if (json.substring(i, i + 4) == "false")
			return true;
		else
			return false;
	}

	private static Boolean is_num(String json, int i)
	{
		// A negative literal
		if (json.charAt(i) == '-')
			return true;
		else if (json.charAt(i) >= '0' && json.charAt(i) <= '9')
			return true;
		else
			return false;
	}

	public static JElemType elem_type_identify(String json,  String obj_name)
	{
		int       i;
		JElemType obj_type;
		String    search_string = "\"" + obj_name + "\"" + ":";
		Pattern   pattern       = Pattern.compile(search_string);
		Matcher   matcher       = pattern.matcher(json);

		matcher.find();
		// Remove the white space till the first non-trivial character
		i = matcher.end() + 1;
		while (i < json.length() && json.charAt(i) == ' ')
			++i;
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
		else
			obj_type = JElemType.JT_INVAL;
		return obj_type;
	}

	public static void main(String[] args) throws Exception
	{
		String data = new String(Files.readAllBytes(Paths.get("example.json")));
		JElem     obj;
		JElemType obj_type;
		String field_name = args[0];
		int i;

		// Check if the required object is present.
		//if (!is_valid_member(data, "formed"))
		//	return null;
		// Identify the type of the required object.
		obj_type = elem_type_identify(data, field_name);
		//if (obj_type == JElemType.JT_INVAL)
		//	return null;
		// Create an instance of required object type.
		obj = json_elem_create(data, field_name, obj_type);
		System.out.println(obj.je_content);
	}
}
