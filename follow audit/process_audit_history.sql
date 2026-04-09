-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Nov 23, 2025 at 02:50 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `project`
--

-- --------------------------------------------------------

--
-- Table structure for table `process_audit_history`
--

CREATE TABLE `process_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `process_audit_history`
--

INSERT INTO `process_audit_history` (`id`, `auditidpk`, `object`, `event`, `updateType`, `field`, `from`, `to`, `author`, `date`, `lastChange`) VALUES
(1, 209, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Charlie Brown', 'Alice Smith', '2025-11-02 16:44:02', '2025-11-02 16:44:02'),
(1, 210, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-02 16:44:02', '2025-11-02 16:44:02'),
(1, 211, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-02 16:44:02', '2025-11-02 16:44:02'),
(10, 1, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'dfsdfsdfs', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 2, 'Process', 'Details', 'Added', 'Description', NULL, 'sdfsdfsdf', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 3, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'fsdfsdf', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 4, 'Process', 'Details', 'Added', 'Parent Process', NULL, 'ff', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 5, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 6, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 7, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 8, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 9, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 10, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(10, 11, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'System', '2025-10-22 16:20:41', '2025-10-22 16:20:41'),
(12, 12, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'sdfsdf', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 13, 'Process', 'Details', 'Added', 'Description', NULL, 'sdfs', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 14, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'sdfsdf', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 15, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 16, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 17, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 18, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 19, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 20, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(12, 21, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'System', '2025-10-22 16:45:33', '2025-10-22 16:45:33'),
(13, 22, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'fgdsfg', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 23, 'Process', 'Details', 'Added', 'Description', NULL, 'gdfgdfg', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 24, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'gdfgdfg', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 25, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 26, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 27, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 28, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 29, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 30, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(13, 31, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'System', '2025-10-22 16:48:41', '2025-10-22 16:48:41'),
(14, 32, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'dsfgsdfsdfg', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 33, 'Process', 'Details', 'Added', 'Description', NULL, 'dfgdf', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 34, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'dfgdfg', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 35, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 36, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 37, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 38, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 39, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 40, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(14, 41, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'System', '2025-10-22 16:50:25', '2025-10-22 16:50:25'),
(15, 42, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'سيبيب', 'System', '2025-10-22 16:57:01', '2025-10-22 16:57:01'),
(15, 43, 'Process', 'Details', 'Added', 'Description', NULL, 'سيبسيب', 'System', '2025-10-22 16:57:01', '2025-10-22 16:57:01'),
(15, 44, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'سيبسي', 'System', '2025-10-22 16:57:01', '2025-10-22 16:57:01'),
(15, 45, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-22 16:57:01', '2025-10-22 16:57:01'),
(15, 46, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'System', '2025-10-22 16:57:02', '2025-10-22 16:57:02'),
(15, 47, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'System', '2025-10-22 16:57:02', '2025-10-22 16:57:02'),
(15, 48, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'System', '2025-10-22 16:57:02', '2025-10-22 16:57:02'),
(15, 49, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'System', '2025-10-22 16:57:02', '2025-10-22 16:57:02'),
(15, 50, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'System', '2025-10-22 16:57:02', '2025-10-22 16:57:02'),
(15, 51, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'System', '2025-10-22 16:57:02', '2025-10-22 16:57:02'),
(16, 52, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'sdfsd', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 53, 'Process', 'Details', 'Added', 'Description', NULL, 'sdf', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 54, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'sdf', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 55, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 56, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 57, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 58, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 59, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 60, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 61, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 62, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 63, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(16, 64, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'System', '2025-10-23 10:24:20', '2025-10-23 10:24:20'),
(17, 65, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'بليبل', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 66, 'Process', 'Details', 'Added', 'Description', NULL, 'يبسليبل', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 67, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'يبل', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 68, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 69, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 70, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 71, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 72, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 73, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 74, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 75, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 76, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(17, 77, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 15:49:37', '2025-10-26 15:49:37'),
(18, 78, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'fsdfsd', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 79, 'Process', 'Details', 'Added', 'Description', NULL, 'يبليبل', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 80, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'ليبلsdfsdf', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 81, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 82, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 83, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 84, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 85, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 86, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 87, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 88, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 89, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(18, 90, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-26 16:09:06', '2025-10-26 16:09:06'),
(19, 91, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'cgbcb', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 92, 'Process', 'Details', 'Added', 'Description', NULL, 'bcvxbc', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 93, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'cbxvb', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 94, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 95, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 96, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 97, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 98, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 99, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 100, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 101, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 102, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(19, 103, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:23:52', '2025-10-28 10:23:52'),
(20, 104, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'gdf', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 105, 'Process', 'Details', 'Added', 'Description', NULL, 'gfhfgc', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 106, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'werwe', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 107, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 108, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 109, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 110, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 111, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 112, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 113, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 114, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 115, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 116, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 117, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:39:09', '2025-10-28 10:39:09'),
(20, 118, 'Process', 'Details', 'Updated', 'Primary Name', 'gdf', 'gdf2', 'Alice Smith', '2025-10-28 10:39:23', '2025-10-28 10:39:23'),
(20, 119, 'Process', 'Details', 'Updated', 'Input Description', '', NULL, 'Alice Smith', '2025-10-28 10:39:23', '2025-10-28 10:39:23'),
(20, 120, 'Process', 'Details', 'Updated', 'Output Description', '', NULL, 'Alice Smith', '2025-10-28 10:39:23', '2025-10-28 10:39:23'),
(20, 121, 'Process', 'Details', 'Updated', 'Parent Process', NULL, NULL, 'Alice Smith', '2025-10-28 10:39:23', '2025-10-28 10:39:23'),
(20, 122, 'Process', 'Details', 'Updated', 'Description', 'gfhfgc', 'gfhfgc3', 'Alice Smith', '2025-10-28 10:41:21', '2025-10-28 10:41:21'),
(20, 123, 'Process', 'Details', 'Updated', 'Parent Process', NULL, NULL, 'Alice Smith', '2025-10-28 10:41:21', '2025-10-28 10:41:21'),
(20, 124, 'Process', 'Details', 'Updated', 'Primary Name', 'gdf2', 'gdf23', 'Alice Smith', '2025-10-28 10:42:01', '2025-10-28 10:42:01'),
(20, 125, 'Process', 'Details', 'Updated', 'Parent Process', NULL, NULL, 'Alice Smith', '2025-10-28 10:42:01', '2025-10-28 10:42:01'),
(21, 126, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'asdasdf', 'Alice Smith', '2025-10-28 10:44:23', '2025-10-28 10:44:23'),
(21, 127, 'Process', 'Details', 'Added', 'Description', NULL, 'fsdfsdf', 'Alice Smith', '2025-10-28 10:44:23', '2025-10-28 10:44:23'),
(21, 128, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'sdfsd', 'Alice Smith', '2025-10-28 10:44:23', '2025-10-28 10:44:23'),
(21, 129, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:44:23', '2025-10-28 10:44:23'),
(21, 130, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-28 10:44:23', '2025-10-28 10:44:23'),
(21, 131, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 132, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 133, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 134, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 135, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 136, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 137, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 138, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 139, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:44:24', '2025-10-28 10:44:24'),
(21, 140, 'Process', 'Details', 'Updated', 'Primary Name', 'asdasdf', 'asdasdf21', 'Alice Smith', '2025-10-28 10:44:45', '2025-10-28 10:44:45'),
(21, 141, 'Process', 'Details', 'Updated', 'Parent Process', NULL, NULL, 'Alice Smith', '2025-10-28 10:44:45', '2025-10-28 10:44:45'),
(22, 142, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'yetrdhfghdf', 'Alice Smith', '2025-10-28 10:46:32', '2025-10-28 10:46:32'),
(22, 143, 'Process', 'Details', 'Added', 'Description', NULL, 'fghdfh', 'Alice Smith', '2025-10-28 10:46:32', '2025-10-28 10:46:32'),
(22, 144, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'fgdhdfgh', 'Alice Smith', '2025-10-28 10:46:32', '2025-10-28 10:46:32'),
(22, 145, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:46:32', '2025-10-28 10:46:32'),
(22, 146, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 147, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 148, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 149, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 150, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 151, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 152, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 153, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 154, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 155, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 10:46:33', '2025-10-28 10:46:33'),
(22, 156, 'Process', 'Details', 'Updated', 'Duration', '1', '2', 'Alice Smith', '2025-10-28 10:47:08', '2025-10-28 10:47:08'),
(22, 157, 'Process', 'Details', 'Updated', 'Primary Name', 'yetrdhfghdf', 'yetrdhfghdf2', 'Alice Smith', '2025-10-28 10:47:20', '2025-10-28 10:47:20'),
(22, 158, 'Process', 'Details', 'Updated', 'Parent Process', NULL, 'fsdfsd', 'Alice Smith', '2025-10-28 10:47:31', '2025-10-28 10:47:31'),
(22, 159, 'Process', 'Details', 'Updated', 'Primary Name', 'yetrdhfghdf2', 'yetrdhfghdf233333', 'Alice Smith', '2025-10-28 10:53:22', '2025-10-28 10:53:22'),
(22, 160, 'Process', 'Details', 'Updated', 'Primary Name', 'yetrdhfghdf233333', 'Abdo', 'Alice Smith', '2025-10-28 10:59:28', '2025-10-28 10:59:28'),
(23, 161, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'Ahmed', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 162, 'Process', 'Details', 'Added', 'Description', NULL, 'ahmed process', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 163, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'AH1', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 164, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 165, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 166, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 167, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 168, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 169, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 170, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 171, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 11:09:11', '2025-10-28 11:09:11'),
(23, 172, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 11:09:12', '2025-10-28 11:09:12'),
(23, 173, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 11:09:12', '2025-10-28 11:09:12'),
(23, 174, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 11:09:12', '2025-10-28 11:09:12'),
(23, 175, 'Process', 'Details', 'Updated', 'Primary Name', 'Ahmed', 'Ahmed 2', 'Alice Smith', '2025-10-28 11:11:18', '2025-10-28 11:11:18'),
(23, 176, 'Process', 'Details', 'Updated', 'Primary Name', 'Ahmed 2', 'Ahmed 1', 'admin admin', '2025-10-28 11:24:08', '2025-10-28 11:24:08'),
(23, 177, 'Process', 'Details', 'Updated', 'Primary Name', 'Ahmed 1', 'Ahmed 12', 'admin admin', '2025-10-28 11:25:46', '2025-10-28 11:25:46'),
(23, 178, 'Process', 'Details', 'Updated', 'Description', 'ahmed process', 'ahmed process2', 'admin admin', '2025-10-28 11:25:46', '2025-10-28 11:25:46'),
(24, 179, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'ghghdfhsd', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 180, 'Process', 'Details', 'Added', 'Description', NULL, 'fghf', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 181, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'fgh', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 182, 'Process', 'Details', 'Status Change', 'Status', NULL, '1', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 183, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, '1', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 184, 'Process', 'Details', 'Added', 'Process Type', NULL, '1', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 185, 'Process', 'Details', 'Added', 'Is Public', NULL, '1', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 186, 'Process', 'Details', 'Added', 'Process Class', NULL, '1', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 187, 'Process', 'Details', 'Added', 'Process Automation', NULL, '1', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 188, 'Process', 'Details', 'Added', 'Duration Type', NULL, '2', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 189, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 190, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 191, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(24, 192, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 13:50:04', '2025-10-28 13:50:04'),
(25, 193, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'gfhdfhfgh', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 194, 'Process', 'Details', 'Added', 'Description', NULL, 'h', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 195, 'Process', 'Details', 'Added', 'Reference Number', NULL, 'dfgh', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 196, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 197, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 198, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 199, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Public', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 200, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 201, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 202, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd212', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 203, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 204, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Admin', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 205, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 206, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-10-28 13:55:38', '2025-10-28 13:55:38'),
(25, 207, 'Process', 'Details', 'Updated', 'Primary Name', 'gfhdfhfgh', 'gfhdfhfghhkljj', 'Alice Smith', '2025-10-28 13:55:55', '2025-10-28 13:55:55'),
(25, 208, 'Stakeholder', 'edit', 'Updated', 'Name', 'Alice Smith', 'Charlie Brown', 'System', '2025-10-30 16:32:22', '2025-10-30 16:32:22'),
(26, 212, 'Process', 'Details', 'Added', 'Primary Name', NULL, 'process test4', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 213, 'Process', 'Details', 'Added', 'Description', NULL, '9/11', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 214, 'Process', 'Details', 'Added', 'Parent Process', NULL, 'process test3', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 215, 'Process', 'Details', 'Status Change', 'Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 216, 'Process', 'Details', 'Status Change', 'Lifecycle Status', NULL, 's1', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 217, 'Process', 'Details', 'Added', 'Process Type', NULL, 't1 p', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 218, 'Process', 'Details', 'Added', 'Is Public', NULL, 'Internal', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 219, 'Process', 'Details', 'Added', 'Process Class', NULL, 'c1', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 220, 'Process', 'Details', 'Added', 'Process Automation', NULL, 'au1', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 221, 'Process', 'Details', 'Added', 'Duration Type', NULL, 'd1', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 222, 'Process', 'Details', 'Added', 'Created By', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 223, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 224, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 225, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Alice Smith', 'Alice Smith', '2025-11-09 13:29:32', '2025-11-09 13:29:32'),
(26, 226, 'Stakeholder', 'delete', 'Deleted', 'Name', 'Alice Smith', NULL, 'System', '2025-11-09 13:35:10', '2025-11-09 13:35:10'),
(26, 227, 'Stakeholder', 'delete', 'Deleted', 'Role', 'Change Request OwnerR', NULL, 'System', '2025-11-09 13:35:10', '2025-11-09 13:35:10'),
(26, 228, 'Stakeholder', 'delete', 'Deleted', 'Role Status', 'Active', NULL, 'System', '2025-11-09 13:35:10', '2025-11-09 13:35:10'),
(26, 229, 'Stakeholder', 'link', 'Added', 'Name', NULL, 'Charlie Brown', 'Alice Smith', '2025-11-09 13:35:10', '2025-11-09 13:35:10'),
(26, 230, 'Stakeholder', 'link', 'Added', 'Role', NULL, 'Change Request OwnerR', 'Alice Smith', '2025-11-09 13:35:10', '2025-11-09 13:35:10'),
(26, 231, 'Stakeholder', 'link', 'Added', 'Role Status', NULL, 'Active', 'Alice Smith', '2025-11-09 13:35:10', '2025-11-09 13:35:10');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `process_audit_history`
--
ALTER TABLE `process_audit_history`
  ADD PRIMARY KEY (`id`,`auditidpk`),
  ADD UNIQUE KEY `auditidpk` (`auditidpk`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `process_audit_history`
--
ALTER TABLE `process_audit_history`
  MODIFY `auditidpk` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=232;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `process_audit_history`
--
ALTER TABLE `process_audit_history`
  ADD CONSTRAINT `Process_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `process` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
