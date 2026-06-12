"""
ComfyUI Workflow List API Plugin
=================================
Install: Copy this folder to ComfyUI/custom_nodes/workflow_list_api/

Provides a /workflow_list endpoint that returns all .json workflow files
found in the user's workflows directories, as well as a /workflow_file
endpoint to fetch individual workflow content.
"""

import os
import json
import glob
import folder_paths
from aiohttp import web

WEB_DIRECTORY = None
NODE_CLASS_MAPPINGS = {}
NODE_DISPLAY_NAME_MAPPINGS = {}

WORKFLOW_DIRS = []

def find_workflow_dirs():
    """Find all directories that may contain workflow files."""
    dirs = []
    base = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    
    # Standard user workflows directory
    user_workflows = os.path.join(base, "user", "default", "workflows")
    if os.path.isdir(user_workflows):
        dirs.append(("User Workflows", user_workflows))
    
    # Alternative locations
    for name in ["workflows", "my_workflows", "output"]:
        alt = os.path.join(base, name)
        if os.path.isdir(alt) and alt not in [d[1] for d in dirs]:
            dirs.append((name.replace("_", " ").title(), alt))
    
    # Also check output directory for workflow JSONs
    output_dir = folder_paths.get_output_directory()
    if os.path.isdir(output_dir) and output_dir not in [d[1] for d in dirs]:
        dirs.append(("Output", output_dir))
    
    return dirs


def scan_workflows():
    """Scan all workflow directories and return workflow metadata."""
    global WORKFLOW_DIRS
    WORKFLOW_DIRS = find_workflow_dirs()
    
    workflows = []
    for category, directory in WORKFLOW_DIRS:
        try:
            pattern = os.path.join(directory, "**", "*.json")
            for filepath in glob.glob(pattern, recursive=True):
                try:
                    stat = os.stat(filepath)
                    relpath = os.path.relpath(filepath, directory)
                    workflows.append({
                        "name": os.path.splitext(os.path.basename(filepath))[0],
                        "path": relpath.replace("\\", "/"),
                        "category": category,
                        "size": stat.st_size,
                        "modified": stat.st_mtime
                    })
                except OSError:
                    continue
        except Exception:
            continue
    
    # Sort by modified time, most recent first
    workflows.sort(key=lambda w: w["modified"], reverse=True)
    return workflows


async def handle_workflow_list(request):
    """API: GET /workflow_list - returns list of all available workflows."""
    try:
        workflows = scan_workflows()
        return web.json_response({
            "success": True,
            "workflows": workflows,
            "total": len(workflows)
        })
    except Exception as e:
        return web.json_response({
            "success": False,
            "error": str(e)
        }, status=500)


async def handle_workflow_file(request):
    """API: GET /workflow_file?path=...&category=... - returns file content."""
    path = request.query.get("path", "")
    category = request.query.get("category", "")
    
    if not path:
        return web.json_response({"success": False, "error": "Missing path"}, status=400)
    
    # Security: prevent directory traversal
    path = path.replace("\\", "/").lstrip("/")
    if ".." in path:
        return web.json_response({"success": False, "error": "Invalid path"}, status=400)
    
    # Find the correct directory
    target_dir = None
    for cat, directory in WORKFLOW_DIRS:
        if cat == category or not category:
            full = os.path.join(directory, path)
            if os.path.isfile(full):
                target_dir = directory
                break
    
    if not target_dir:
        # Try all directories
        for cat, directory in WORKFLOW_DIRS:
            full = os.path.join(directory, path)
            if os.path.isfile(full):
                target_dir = directory
                break
    
    if not target_dir:
        return web.json_response({"success": False, "error": "File not found"}, status=404)
    
    filepath = os.path.join(target_dir, path)
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
        return web.json_response({
            "success": True,
            "name": os.path.splitext(os.path.basename(path))[0],
            "content": content
        })
    except Exception as e:
        return web.json_response({"success": False, "error": str(e)}, status=500)


# Register routes with ComfyUI server
def init_routes():
    try:
        import server
        server_instance = server.PromptServer.instance
        server_instance.app.router.add_get("/workflow_list", handle_workflow_list)
        server_instance.app.router.add_get("/workflow_file", handle_workflow_file)
        print("[WorkflowListAPI] Routes registered: /workflow_list, /workflow_file")
    except Exception as e:
        print(f"[WorkflowListAPI] Failed to register routes: {e}")


# Try to register immediately, also register on load
try:
    init_routes()
except Exception:
    pass

print("[WorkflowListAPI] Plugin loaded. Install in ComfyUI/custom_nodes/workflow_list_api/")
