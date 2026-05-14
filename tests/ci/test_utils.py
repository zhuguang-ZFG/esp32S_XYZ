"""
CI 测试工具模块

提供通用的测试工具函数，用于 CI 相关的测试。
"""

import json
import os
from pathlib import Path
from typing import Any, Dict, Optional


def load_json_file(file_path: str) -> Dict[str, Any]:
    """
    加载 JSON 文件
    
    Args:
        file_path: JSON 文件路径
        
    Returns:
        解析后的 JSON 对象
        
    Raises:
        FileNotFoundError: 文件不存在
        json.JSONDecodeError: JSON 格式错误
    """
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_json_file(file_path: str, data: Dict[str, Any]) -> None:
    """
    保存 JSON 文件
    
    Args:
        file_path: JSON 文件路径
        data: 要保存的数据
    """
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def get_project_root() -> Path:
    """
    获取项目根目录
    
    Returns:
        项目根目录的 Path 对象
    """
    # 从当前文件向上查找，直到找到 .git 目录
    current = Path(__file__).resolve()
    for parent in current.parents:
        if (parent / '.git').exists():
            return parent
    # 如果没找到 .git，返回当前文件的祖父目录（tests/ci/ -> tests/ -> root/）
    return current.parents[2]


def get_schemas_dir() -> Path:
    """
    获取 schemas 目录
    
    Returns:
        schemas 目录的 Path 对象
    """
    return get_project_root() / 'docs' / 'schemas'


def get_fixtures_dir() -> Path:
    """
    获取测试 fixtures 目录
    
    Returns:
        fixtures 目录的 Path 对象
    """
    return Path(__file__).parent / 'fixtures'


def create_temp_file(content: str, suffix: str = '.json') -> Path:
    """
    创建临时文件
    
    Args:
        content: 文件内容
        suffix: 文件后缀
        
    Returns:
        临时文件的 Path 对象
    """
    import tempfile
    fd, path = tempfile.mkstemp(suffix=suffix, text=True)
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(content)
    return Path(path)
