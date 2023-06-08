import requests
import json
import os

from typing import Optional, Tuple
from pprint import pprint as print


def create_index(endpoint: str, index_name: str, auth: Optional[Tuple[str, str]] = None, data: Optional[dict] = None):
    response = requests.put(f'{endpoint}/{index_name}', auth=auth, verify=False)

    return response


def check_index(endpoint: str, index_name: str, auth: Optional[Tuple[str, str]] = None, data: Optional[dict] = None):
    response = requests.get(f'{endpoint}/{index_name}', auth=auth, verify=False)

    return response


def delete_index(endpoint: str, index_name: str, auth: Optional[Tuple[str, str]] = None, data: Optional[dict] = None):
    response = requests.delete(f'{endpoint}/{index_name}', auth=auth, verify=False)

    return response


def delete_document(endpoint: str, index_name: str, doc_id: str, auth: Optional[Tuple[str, str]] = None):
    response = requests.delete(f'{endpoint}/{index_name}/_doc/{doc_id}', auth=auth, verify=False)

    return response


def create_document(endpoint: str, index_name: str, doc_id: str, auth: Optional[Tuple[str, str]] = None,
                    data: Optional[dict] = None):
    document = {
        'title': 'Test Document',
        'content': 'This is a sample document for testing OpenSearch.'
    }
    url = f'{endpoint}/{index_name}/_doc/{doc_id}'
    headers = {'Content-Type': 'application/json'}

    response = requests.put(url, headers=headers, data=json.dumps(document), auth=auth, verify=False)

    return response


def check_document(endpoint, index_name, doc_id, auth=None):
    url = f'{endpoint}/{index_name}/_doc/{doc_id}'
    headers = {'Content-Type': 'application/json'}

    response = requests.get(url, headers=headers, auth=auth, verify=False)

    return response


def get_document(endpoint: str, index_name: str, doc_id: str, auth: Optional[Tuple[str, str]] = None):
    url = f'{endpoint}/{index_name}/_doc/{doc_id}'
    headers = {'Content-Type': 'application/json'}

    response = requests.get(url, headers=headers, auth=auth, verify=False)
    document = response.json()
    content = document['_source']

    return content


def main():

    username = os.getenv('USERNAME', 'admin')
    password = os.getenv('PASSWORD', 'admin')
    endpoint = os.getenv('ENDPOINT', 'https://localhost:9200')  # Dont forget port number.

    auth = (username, password)
    index = 'my_index4'
    doc_id = '7'

    response = create_index(endpoint, index, auth)
    print(response.status_code)

    notebook_file_path = "ResultsRepository.ipynb"

    # Set the Jupyter Notebook server URL and access token
    base_url = "http://localhost:8888"
    access_token = "b09873c66949d5aa7e36514a86c1ec9a2ed0da9bf7d12283"

    # Prepare the API endpoint URL
    url = f"{base_url}/api/contents/{notebook_file_path}?token={access_token}"

    # Send a GET request to retrieve the notebook data
    response = requests.get(url)
    data = response.json()
    print(data)

    # Handle the response
    if response.status_code == 200:
        notebook_data = response.json()
        print(notebook_data)
        # Process the notebook data as needed
        # Access specific fields using dictionary access, e.g., notebook_data["name"]
    else:
        print("Failed to retrieve notebook data. Status code:", response.status_code)

    # Convert the response to JSON

    # Extract the cell contents
    cell_contents = [cell['source'] for cell in data['content'] if cell['type'] == 'code']

    # Print the cell contents
    for cell_content in cell_contents:
        print(cell_content)

    create_index(endpoint, index, auth)
    create_document(endpoint, index, doc_id, auth)
    delete_document(endpoint, index, doc_id, auth)
    delete_index(endpoint, index, auth)


if __name__ == "__main__":
    main()
