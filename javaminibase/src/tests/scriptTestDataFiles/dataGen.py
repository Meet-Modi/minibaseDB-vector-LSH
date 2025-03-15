import random
import string

def generate_integer():
    # Generates a random integer between 0 and 100.
    return random.randint(0, 100)

def generate_real():
    # Generates a random float between 0.0 and 100.0 rounded to 2 decimal places.
    return round(random.uniform(0.0, 100.0), 2)

def generate_string():
    # Generates a random string of 8 alphabetical characters.
    return ''.join(random.choices(string.ascii_letters, k=8))

def generate_100d_vector():
    # Generates a string of 100 integers (each between -10000 and 10000) separated by spaces.
    return ' '.join(str(random.randint(-10000, 10000)) for _ in range(100))

def generate_tuple(attribute_types):
    """
    Generates one tuple (record) of data based on the list of attribute types.
    For each attribute type:
      - 1: integer
      - 2: real
      - 3: string
      - 4: 100D-vector
    """
    values = []
    for attr_type in attribute_types:
        if attr_type == 1:
            values.append(str(generate_integer()))
        elif attr_type == 2:
            values.append(str(generate_real()))
        elif attr_type == 3:
            values.append(generate_string())
        elif attr_type == 4:
            values.append(generate_100d_vector())
        else:
            values.append("N/A")
    return values

def main():
    # Query the user for the number of 100D vector type attributes.
    num_vector_attributes = int(input("Enter the number of 100D vector type attributes: "))
    
    # Define fixed non-vector attributes.
    # For this example, we include:
    # 1 (integer), 2 (real), and 3 (string)
    fixed_attributes = [1, 2, 3]
    
    # Combine fixed attributes with the 100D vector attributes.
    attribute_types = fixed_attributes + [4] * num_vector_attributes
    n_attributes = len(attribute_types)
    
    # Query the user for the number of tuples (records) to generate.
    num_tuples = int(input("Enter the number of tuples to generate: "))
    
    # Open the file for writing.
    with open("sample_data.txt", "w") as f:
        # First line: number of attributes.
        f.write(str(n_attributes) + "\n")
        
        # Second line: attribute types separated by spaces.
        f.write(" ".join(map(str, attribute_types)) + "\n")
        
        # Write each tuple (record) to the file.
        # Each tuple consists of n attributes, one per line.
        for _ in range(num_tuples):
            tuple_values = generate_tuple(attribute_types)
            for value in tuple_values:
                f.write(value + "\n")
    
    print("Sample data file 'sample_data.txt' generated successfully.")

if __name__ == "__main__":
    main()