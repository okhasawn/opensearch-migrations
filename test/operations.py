import requests
import json
import logging

logger = logging.getLogger(__name__)


def create_index(endpoint: str, index_name: str, auth, verify_ssl: bool = False, protocol: str = 'https'):
    logger.debug(f"Creating index: {index_name} at endpoint: {endpoint}")
    response = requests.put(f'{endpoint}/{index_name}', auth=auth, verify=verify_ssl)
    logger.debug(f"Response status: {response.status_code}, Body: {response.text}")

    return response


def check_index(endpoint: str, index_name: str, auth, verify_ssl: bool = False, protocol: str = 'https'):
    logger.debug(f"checking index: {index_name} at endpoint: {endpoint}")
    response = requests.get(f'{endpoint}/{index_name}', auth=auth, verify=verify_ssl)
    logger.debug(f"Response status: {response.status_code}, Body: {response.text}")

    return response


def delete_index(endpoint: str, index_name: str, auth, verify_ssl: bool = False):
    logger.debug(f"deleting index: {index_name} at endpoint: {endpoint}")
    response = requests.delete(f'{endpoint}/{index_name}', auth=auth, verify=verify_ssl)
    logger.debug(f"Response status: {response.status_code}, Body: {response.text}")

    return response


def delete_document(endpoint: str, index_name: str, doc_id: str, auth,
                    verify_ssl: bool = False):
    logger.debug(f"deleting document: {index_name} at endpoint: {endpoint}")
    response = requests.delete(f'{endpoint}/{index_name}/_doc/{doc_id}', auth=auth, verify=verify_ssl)
    logger.debug(f"Response status: {response.status_code}, Body: {response.text}")

    return response


def create_document(endpoint: str, index_name: str, doc_id: str, auth,
                    verify_ssl: bool = False):
    document = {
        'title': 'Test Document',
        'content': 'This is a sample document for testing OpenSearch.'
    }
    url = f'{endpoint}/{index_name}/_doc/{doc_id}'
    headers = {'Content-Type': 'application/json'}
    logger.debug(f"Creating document: {index_name} at endpoint: {endpoint}")
    response = requests.put(url, headers=headers, data=json.dumps(document), auth=auth, verify=verify_ssl)
    logger.debug(f"Response status: {response.status_code}, Body: {response.text}")

    return response


def get_document(endpoint: str, index_name: str, doc_id: str, auth,
                 verify_ssl: bool = False):
    url = f'{endpoint}/{index_name}/_doc/{doc_id}'
    headers = {'Content-Type': 'application/json'}
    logger.debug(f"getting document : {index_name} at endpoint: {endpoint}")
    response = requests.get(url, headers=headers, auth=auth, verify=verify_ssl)
    logger.debug(f"Response status: {response.status_code}, Body: {response.text}")

    return response
