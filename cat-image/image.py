import cv2
import numpy as np

def process_cat_image(input_path, output_path, close_kernel_size=3):
    # 读取图片（带 alpha 通道）
    img = cv2.imread(input_path, cv2.IMREAD_UNCHANGED)
    if img.shape[2] == 3:
        # 如果没有 alpha 通道，添加白色背景
        img = cv2.cvtColor(img, cv2.COLOR_BGR2BGRA)
    
    # 提取白色背景区域（假设背景接近白色）
    lower_white = np.array([200, 200, 200, 0])
    upper_white = np.array([255, 255, 255, 255])
    mask = cv2.inRange(img[:, :, :3], lower_white, upper_white)
    
    # 反转 mask：黑色线条区域
    line_mask = cv2.bitwise_not(mask)
    
    # 形态学闭运算，闭合小断裂
    kernel = np.ones((close_kernel_size, close_kernel_size), np.uint8)
    line_mask = cv2.morphologyEx(line_mask, cv2.MORPH_CLOSE, kernel)
    
    # 创建透明背景结果
    result = np.zeros((img.shape[0], img.shape[1], 4), dtype=np.uint8)
    result[line_mask == 255] = [0, 0, 0, 255]   # 黑色线条，完全不透明
    result[line_mask == 0] = [0, 0, 0, 0]       # 其他区域全透明
    
    cv2.imwrite(output_path, result)
    print(f"处理完成，已保存到 {output_path}")

# 调用示例
process_cat_image("cat_original.png", "cat_processed.png", close_kernel_size=3)